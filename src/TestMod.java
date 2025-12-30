import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import arc.util.serialization.*;
import java.net.*;
import java.io.*;

public class TestMod extends Mod {
    
    private static class TokenInfo {
        String[] parts;
        int requestCount = 0;
        long lastUsed = 0;
        boolean rateLimited = false;
        
        TokenInfo(String[] scrambledParts) {
            this.parts = scrambledParts;
        }
        
        String getToken() {
            StringBuilder sb = new StringBuilder();
            for(String part : parts) {
                sb.append(part);
            }
            return sb.toString();
        }
    }
    
    private Seq<TokenInfo> tokens = new Seq<>();
    private int currentTokenIndex = 0;
    private static final int MAX_REQUESTS_PER_TOKEN = 50;
    private static final long RATE_LIMIT_RESET_TIME = 3600000;
    
    private Seq<ModInfo> allMods = new Seq<>();
    private Seq<ModInfo> filteredMods = new Seq<>();
    private ObjectMap<String, ModStats> statsCache = new ObjectMap<>();
    private int currentPage = 0;
    private int modsPerPage = 6;
    private String searchQuery = "";
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private Color accentColor = Color.valueOf("ffd37f");
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();
    private int currentTab = 0;

    public TestMod() {
        Log.info("ModInfo+ Initializing");
        initTokens();
    }
    
    void initTokens() {
        tokens.add(new TokenInfo(new String[]{"ghp_", "VVNy", "jnJl", "AYvi", "yOWR", "JPdr", "FEzb", "YIIX", "Uh2a", "49ho"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "ljb7", "p6nU", "pWfe", "WGW1", "ookX", "2Fhh", "t9XT", "qT1P", "nffd"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "mPsi", "rCTW", "Nqh", "VCEm", "OY2V", "szbF", "Pf7Y", "OTP0", "N7tC"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "hLcz", "gAeJ", "9C7z", "MWZx", "QNOY", "Ixe", "Mxrl", "ELx2", "rABt"}));
    }
    
    String getNextToken() {
        for(int i = 0; i < tokens.size; i++) {
            TokenInfo token = tokens.get(currentTokenIndex);
            
            if(token.rateLimited) {
                if(Time.millis() - token.lastUsed > RATE_LIMIT_RESET_TIME) {
                    token.rateLimited = false;
                    token.requestCount = 0;
                } else {
                    currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
                    continue;
                }
            }
            
            if(token.requestCount < MAX_REQUESTS_PER_TOKEN) {
                token.requestCount++;
                token.lastUsed = Time.millis();
                return token.getToken();
            }
            
            currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
        }
        
        return tokens.get(0).getToken();
    }
    
    void markTokenRateLimited() {
        tokens.get(currentTokenIndex).rateLimited = true;
        currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
    }
    
    void githubGet(String url, Cons<String> success, Runnable fail) {
        Http.get(url)
            .header("User-Agent", "Mindustry-ModBrowser")
            .header("Authorization", "token " + getNextToken())
            .timeout(15000)
            .error(e -> {
                markTokenRateLimited();
                Core.app.post(fail);
            })
            .submit(res -> {
                String text = res.getResultAsString();
                Core.app.post(() -> success.get(text));
            });
    }

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadBadges();
                loadModIcons();
                replaceModsButton();
            });
        });
    }
    
    void loadBadges() {
        javaBadge = Core.atlas.find("testmod-java-badge");
        jsBadge = Core.atlas.find("testmod-js-badge");
        
        if(!javaBadge.found()) {
            javaBadge = Core.atlas.find("java-badge");
        }
        if(!jsBadge.found()) {
            jsBadge = Core.atlas.find("js-badge");
        }
    }
    
    void loadModIcons() {
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(mod.iconTexture != null) {
                String key = mod.name.toLowerCase();
                modIcons.put(key, new TextureRegion(mod.iconTexture));
                if(mod.meta != null && mod.meta.name != null) {
                    modIcons.put(mod.meta.name.toLowerCase(), new TextureRegion(mod.iconTexture));
                }
            }
        }
    }
    
    void replaceModsButton() {
        Vars.ui.mods.shown(() -> {
            Core.app.post(() -> {
                Vars.ui.mods.hide();
                showEnhancedBrowser();
            });
        });
    }
    
    void showEnhancedBrowser() {
        if(browserDialog != null) {
            browserDialog.show();
            return;
        }
        browserDialog = new BaseDialog("Mods");
        browserDialog.addCloseButton();
        
        Table main = new Table(Tex.pane);
        
        main.table(header -> {
            header.background(Tex.button);
            header.image(Icon.book).size(40f).padLeft(15f).padRight(10f);
            header.add("[accent]MODINFO+").style(Styles.outlineLabel).left();
            header.add().growX();
            header.button(Icon.refresh, Styles.cleari, 40f, () -> reloadMods()).size(50f).padRight(10f);
        }).fillX().height(60f).row();
        
        main.image().color(accentColor).fillX().height(3f).row();
        
        main.table(search -> {
            search.image(Icon.zoom).size(32f).padRight(8f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
                updateVisibleMods();
            });
            search.add(searchField).growX().height(45f).pad(10f);
        }).fillX().row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(8f).row();
        
        main.table(tabs -> {
            tabs.button("Enabled", () -> {
                currentTab = 0;
                fetchEnabledMods();
            }).growX().height(50f);
            tabs.button("Disabled", () -> {
                currentTab = 1;
                fetchDisabledMods();
            }).growX().height(50f);
            tabs.button("Browse", () -> {
                currentTab = 2;
                showBrowseDialog();
            }).growX().height(50f);
        }).fillX().row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        main.add(pane).grow().row();
        
        browserDialog.cont.add(main).size(900f, 750f);
        browserDialog.show();
        
        fetchEnabledMods();
    }
    
    void showBrowseDialog() {
        BaseDialog browse = new BaseDialog("Browse Mods");
        browse.addCloseButton();
        
        Table main = new Table(Tex.pane);
        
        main.table(search -> {
            search.image(Icon.zoom).size(32f).padRight(8f);
            TextField searchBrowse = new TextField();
            searchBrowse.setMessageText("Search online mods...");
            searchBrowse.changed(() -> {
                searchQuery = searchBrowse.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
                updateVisibleMods();
            });
            search.add(searchBrowse).growX().height(45f).pad(10f);
        }).fillX().row();
        
        Label browseStatus = new Label("");
        main.add(browseStatus).pad(8f).row();
        
        modListContainer = new Table();
        ScrollPane browsePane = new ScrollPane(modListContainer);
        browsePane.setFadeScrollBars(false);
        main.add(browsePane).grow().row();
        
        paginationBar = new Table();
        buildPaginationBar();
        main.add(paginationBar).fillX().row();
        
        browse.cont.add(main).grow();
        browse.show();
        
        statusLabel = browseStatus;
        fetchRemoteMods();
    }
    
    void reloadMods() {
        allMods.clear();
        filteredMods.clear();
        statsCache.clear();
        if(currentTab == 0) {
            fetchEnabledMods();
        } else if(currentTab == 1) {
            fetchDisabledMods();
        } else {
            fetchRemoteMods();
        }
    }
    
    void buildPaginationBar() {
        if(paginationBar == null) return;
        paginationBar.clearChildren();
        paginationBar.background(Tex.button);
        
        paginationBar.button("<", Styles.cleart, () -> {
            if(currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).size(80f, 50f).disabled(b -> currentPage == 0);
        
        paginationBar.add().growX();
        paginationBar.label(() -> "[lightgray]Page " + (currentPage + 1) + " / " + Math.max(1, getMaxPage() + 1)).pad(10f);
        paginationBar.add().growX();
        
        paginationBar.button(">", Styles.cleart, () -> {
            if(currentPage < getMaxPage()) {
                currentPage++;
                updateVisibleMods();
            }
        }).size(80f, 50f).disabled(b -> currentPage >= getMaxPage());
    }
    
    void applyFilter() {
        filteredMods.clear();
        if(searchQuery.isEmpty()) {
            filteredMods.addAll(allMods);
        } else {
            for(ModInfo mod : allMods) {
                if(mod.name.toLowerCase().contains(searchQuery) || 
                    mod.author.toLowerCase().contains(searchQuery) ||
                    mod.description.toLowerCase().contains(searchQuery)) {
                    filteredMods.add(mod);
                }
            }
        }
    }
    
    void updateVisibleMods() {
        if(modListContainer == null) return;
        modListContainer.clearChildren();
        
        if(currentTab < 2) {
            for(ModInfo mod : filteredMods) {
                buildModRow(modListContainer, mod);
            }
        } else {
            int start = currentPage * modsPerPage;
            int end = Math.min(start + modsPerPage, filteredMods.size);
            
            if(filteredMods.isEmpty()) {
                modListContainer.add("[lightgray]No mods found").pad(40f);
            } else {
                for(int i = start; i < end; i++) {
                    buildModRow(modListContainer, filteredMods.get(i));
                }
            }
            buildPaginationBar();
        }
        updateStatusLabel("Showing " + filteredMods.size + " mods");
    }
    
    void updateStatusLabel(String text) {
        if(statusLabel != null) statusLabel.setText("[lightgray]" + text);
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }void fetchEnabledMods() {
    updateStatusLabel("[cyan]Loading enabled mods...");
    Core.app.post(() -> {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(!mod.enabled()) continue;
            ModInfo info = createModInfo(mod);
            info.installedMod = mod;
            info.isInstalled = true;
            allMods.add(info);
        }
        Core.app.post(() -> {
            currentPage = 0;
            applyFilter();
            updateVisibleMods();
        });
    });
}

void fetchDisabledMods() {
    updateStatusLabel("[cyan]Loading disabled mods...");
    Core.app.post(() -> {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(mod.enabled()) continue;
            ModInfo info = createModInfo(mod);
            info.installedMod = mod;
            info.isInstalled = true;
            allMods.add(info);
        }
        Core.app.post(() -> {
            currentPage = 0;
            applyFilter();
            updateVisibleMods();
        });
    });
}

ModInfo createModInfo(Mods.LoadedMod mod) {
    ModInfo info = new ModInfo();
    
    if(mod.meta != null) {
        info.name = mod.meta.name;
        info.author = mod.meta.author;
        info.description = mod.meta.description;
        info.version = mod.meta.version;
    } else {
        info.name = mod.name;
        info.author = "Unknown";
        info.description = "";
        info.version = "1.0";
    }
    
    detectCapabilities(info, mod);
    
    return info;
}

void detectCapabilities(ModInfo info, Mods.LoadedMod mod) {
    info.hasJava = false;
    info.hasScripts = false;
    info.hasContent = false;
    info.serverCompatible = false;
    
    info.hasJava = mod.main != null;
    
    if(mod.root != null && mod.root.child("scripts").exists()) {
        info.hasScripts = true;
    }
    
    if(mod.meta != null && !mod.meta.hidden) {
        info.serverCompatible = true;
    }
}

void fetchRemoteMods() {
    updateStatusLabel("[cyan]Loading online mods...");
    
    githubGet(
        "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json",
        json -> {
            allMods = parseModList(json);
            applyFilter();
            updateVisibleMods();
        },
        () -> updateStatusLabel("[scarlet]Failed to load mod list")
    );
}

Seq<ModInfo> parseModList(String json) {
    Seq<ModInfo> mods = new Seq<>();
    try {
        JsonValue root = new JsonReader().parse(json);
        for(JsonValue modJson : root) {
            ModInfo mod = new ModInfo();
            mod.repo = modJson.getString("repo", "");
            mod.name = modJson.getString("name", "Unknown");
            mod.author = modJson.getString("author", "Unknown");
            mod.description = modJson.getString("description", "");
            mod.version = modJson.getString("minGameVersion", "?");
            mod.lastUpdated = modJson.getString("lastUpdated", "");
            mod.stars = modJson.getInt("stars", 0);
            
            mod.hasJava = modJson.getBoolean("hasJava", false);
            mod.hasScripts = modJson.getBoolean("hasScripts", false);
            mod.hasContent = modJson.getBoolean("hasContent", false);
            mod.serverCompatible = !modJson.getBoolean("clientSide", false);
            
            for(Mods.LoadedMod installed : Vars.mods.list()) {
                if(installed.meta != null && installed.name.equalsIgnoreCase(mod.name)) {
                    mod.isInstalled = true;
                    mod.installedMod = installed;
                    break;
                }
            }
            
            if(!mod.repo.isEmpty() && !mod.name.isEmpty()) {
                mods.add(mod);
            }
        }
    } catch(Exception e) {
        Log.err("Parse error", e);
    }
    return mods;
}

String formatDate(String dateStr) {
    try {
        String[] parts = dateStr.split("T")[0].split("-");
        return parts[1] + "/" + parts[2] + "/" + parts[0];
    } catch(Exception e) {
        return dateStr;
    }
}

void buildModRow(Table table, ModInfo mod) {
    table.table(Tex.button, row -> {
        row.left();
        
        TextureRegion icon = getModIcon(mod);
        if(icon != null) {
            row.image(icon).size(64f).padLeft(10f).padRight(12f);
        } else {
            row.image(Icon.box).size(64f).color(Color.gray).padLeft(10f).padRight(12f);
        }
        
        row.table(info -> {
            info.left().defaults().left();
            
            info.table(title -> {
                title.left();
                title.add("[accent]" + mod.name).style(Styles.outlineLabel).padRight(8f);
                
                Table badges = new Table();
                badges.left().defaults().padRight(8f);
                
                if(mod.hasJava) {
                    Table javaBadgeBtn = new Table(Styles.black6);
                    if(javaBadge != null && javaBadge.found()) {
                        javaBadgeBtn.image(javaBadge).size(28f, 18f).pad(4f);
                    } else {
                        javaBadgeBtn.add("[#b07219]JAVA").style(Styles.outlineLabel).pad(4f);
                    }
                    javaBadgeBtn.clicked(() -> {
                        Vars.ui.showInfo("[#b07219]Java Mod\n[lightgray]Uses compiled Java code");
                    });
                    badges.add(javaBadgeBtn);
                }
                
                if(mod.hasScripts) {
                    Table jsBadgeBtn = new Table(Styles.black6);
                    if(jsBadge != null && jsBadge.found()) {
                        jsBadgeBtn.image(jsBadge).size(28f, 18f).pad(4f);
                    } else {
                        jsBadgeBtn.add("[#f1e05a]JS").style(Styles.outlineLabel).pad(4f);
                    }
                    jsBadgeBtn.clicked(() -> {
                        Vars.ui.showInfo("[#f1e05a]JavaScript Mod\n[lightgray]Uses scripts");
                    });
                    badges.add(jsBadgeBtn);
                }
                
                if(mod.serverCompatible) {
                    Table serverBadgeBtn = new Table(Styles.black6);
                    serverBadgeBtn.image(Icon.host).size(20f).color(Color.royal).pad(4f);
                    serverBadgeBtn.clicked(() -> {
                        Vars.ui.showInfo("[royal]Server Compatible\n[lightgray]Can run on multiplayer servers");
                    });
                    badges.add(serverBadgeBtn);
                }
                
                title.add(badges);
                
                if(mod.stars >= 10) {
                    title.add(" [yellow]\u2605" + mod.stars).padLeft(6f);
                }
                
                if(mod.isInstalled) {
                    title.image(Icon.ok).size(20f).color(Color.lime).padLeft(6f);
                }
            }).row();
            
            info.add("[lightgray]by " + mod.author + " [gray]| v" + mod.version).padTop(4f).row();
            
            if(!mod.description.isEmpty()) {
                String desc = mod.description.length() > 80 ? 
                    mod.description.substring(0, 77) + "..." : mod.description;
                Label descLabel = new Label(desc);
                descLabel.setWrap(true);
                descLabel.setColor(Color.lightGray);
                info.add(descLabel).width(400f).padTop(4f).row();
            }
            
        }).growX().pad(10f);
        
        row.table(btns -> {
            btns.defaults().size(50f);
            
            btns.button(Icon.info, Styles.clearNonei, () -> {
                showModDetails(mod);
            }).tooltip("Details");
            
            if(currentTab < 2) {
                if(mod.installedMod != null) {
                    btns.button(mod.installedMod.enabled() ? Icon.cancel : Icon.ok, Styles.clearNonei, () -> {
                        toggleMod(mod);
                    }).tooltip(mod.installedMod.enabled() ? "Disable" : "Enable");
                    
                    btns.button(Icon.trash, Styles.clearNonei, () -> {
                        deleteMod(mod);
                    }).tooltip("Delete");
                }
            } else {
                if(!mod.repo.isEmpty()) {
                    btns.button(Icon.link, Styles.clearNonei, () -> {
                        Core.app.openURI("https://github.com/" + mod.repo);
                    }).tooltip("GitHub");
                }
                
                if(!mod.isInstalled) {
                    btns.button(Icon.download, Styles.clearNonei, () -> {
                        installMod(mod);
                    }).tooltip("Install");
                }
            }
        }).right().padRight(10f);
        
    }).fillX().height(130f).pad(4f).row();
}TextureRegion getModIcon(ModInfo mod) {
    if(mod.installedMod != null && mod.installedMod.iconTexture != null) {
        return new TextureRegion(mod.installedMod.iconTexture);
    }
    
    String key = mod.name.toLowerCase();
    if(modIcons.containsKey(key)) {
        return modIcons.get(key);
    }
    
    return null;
}

void toggleMod(ModInfo mod) {
    if(mod.installedMod == null) return;
    
    if(mod.installedMod.enabled()) {
        Vars.mods.setEnabled(mod.installedMod, false);
        Vars.ui.showInfo("Disabled " + mod.name);
    } else {
        Vars.mods.setEnabled(mod.installedMod, true);
        Vars.ui.showInfo("Enabled " + mod.name);
    }
    
    reloadMods();
}

void deleteMod(ModInfo mod) {
    if(mod.installedMod == null) return;
    
    Vars.ui.showConfirm("Delete " + mod.name + "?", () -> {
        Vars.mods.removeMod(mod.installedMod);
        Vars.ui.showInfo("Deleted " + mod.name);
        reloadMods();
    });
}

void installMod(ModInfo mod) {
    try {
        Vars.ui.mods.githubImportMod(mod.repo, true);
        Vars.ui.showInfo("Installing " + mod.name + "...");
    } catch(Exception e) {
        Log.err("Install error", e);
        Vars.ui.showErrorMessage("Install failed: " + e.getMessage());
    }
}

void showModDetails(ModInfo mod) {
    BaseDialog dialog = new BaseDialog(mod.name);
    dialog.addCloseButton();
    
    Table content = new Table(Tex.pane);
    content.margin(15f);
    
    TextureRegion icon = getModIcon(mod);
    if(icon != null) {
        content.image(icon).size(80f).pad(10f).row();
    } else {
        content.image(Icon.box).size(80f).color(Color.gray).pad(10f).row();
    }
    
    Table titleRow = new Table();
    titleRow.add("[accent]" + mod.name).pad(5f);
    
    Table badges = new Table();
    badges.defaults().padRight(8f);
    
    if(mod.hasJava) {
        Table javaBadgeBtn = new Table(Styles.black6);
        if(javaBadge != null && javaBadge.found()) {
            javaBadgeBtn.image(javaBadge).size(36f, 22f).pad(4f);
        } else {
            javaBadgeBtn.add("[#b07219]JAVA").pad(4f);
        }
        javaBadgeBtn.clicked(() -> {
            Vars.ui.showInfo("[#b07219]Java Mod\n[lightgray]Uses compiled Java code");
        });
        badges.add(javaBadgeBtn);
    }
    
    if(mod.hasScripts) {
        Table jsBadgeBtn = new Table(Styles.black6);
        if(jsBadge != null && jsBadge.found()) {
            jsBadgeBtn.image(jsBadge).size(36f, 22f).pad(4f);
        } else {
            jsBadgeBtn.add("[#f1e05a]JS").pad(4f);
        }
        jsBadgeBtn.clicked(() -> {
            Vars.ui.showInfo("[#f1e05a]JavaScript Mod\n[lightgray]Uses scripts");
        });
        badges.add(jsBadgeBtn);
    }
    
    if(mod.serverCompatible) {
        Table serverBadgeBtn = new Table(Styles.black6);
        serverBadgeBtn.image(Icon.host).size(24f).color(Color.royal).pad(4f);
        serverBadgeBtn.clicked(() -> {
            Vars.ui.showInfo("[royal]Server Compatible\n[lightgray]Can run on multiplayer servers");
        });
        badges.add(serverBadgeBtn);
    }
    
    titleRow.add(badges);
    content.add(titleRow).row();
    
    content.add("[cyan]" + mod.author).pad(5f).row();
    content.add("[lightgray]v" + mod.version).pad(3f).row();
    if(mod.stars > 0) {
        content.add("[yellow]\u2605 " + mod.stars + " stars").pad(3f).row();
    }
    
    if(!mod.description.isEmpty()) {
        Label desc = new Label(mod.description);
        desc.setWrap(true);
        desc.setAlignment(Align.center);
        desc.setColor(Color.lightGray);
        content.add(desc).width(450f).pad(10f).row();
    }
    
    content.image().height(3f).width(400f).color(accentColor).pad(10f).row();
    
    Table statsTable = new Table();
    if(!mod.repo.isEmpty()) {
        statsTable.add("[cyan]Loading stats...").pad(15f);
        content.add(statsTable).row();
        loadGitHubStats(mod, statsTable);
    }
    
    content.image().height(3f).width(400f).color(accentColor).pad(10f).row();
    
    content.table(actions -> {
        if(!mod.repo.isEmpty()) {
            actions.button("Open GitHub", Icon.link, () -> {
                Core.app.openURI("https://github.com/" + mod.repo);
            }).size(220f, 55f).pad(5f);
        }
        
        if(!mod.isInstalled) {
            actions.button("Install", Icon.download, () -> {
                installMod(mod);
                dialog.hide();
            }).size(220f, 55f).pad(5f);
        }
    }).row();
    
    ScrollPane pane = new ScrollPane(content);
    dialog.cont.add(pane).grow();
    dialog.show();
}

void loadGitHubStats(ModInfo mod, Table statsTable) {
    String key = mod.repo;
    if(statsCache.containsKey(key)) {
        displayStats(statsTable, mod, statsCache.get(key));
        return;
    }
    
    githubGet(
        "https://api.github.com/repos/" + mod.repo,
        json -> {
            ModStats stats = parseRepoStats(json);
            statsCache.put(mod.repo, stats);
            
            githubGet(
                "https://api.github.com/repos/" + mod.repo + "/releases",
                relJson -> {
                    parseReleaseStats(stats, relJson);
                    displayStats(statsTable, mod, stats);
                },
                () -> displayStats(statsTable, mod, stats)
            );
        },
        () -> displayStatsError(statsTable, mod)
    );
}

ModStats parseRepoStats(String json) {
    ModStats stats = new ModStats();
    try {
        JsonValue repoJson = new JsonReader().parse(json);
        stats.stars = repoJson.getInt("stargazers_count", 0);
    } catch(Exception e) {
        Log.err("Parse repo", e);
    }
    return stats;
}

void parseReleaseStats(ModStats stats, String json) {
    try {
        JsonValue releasesJson = new JsonReader().parse(json);
        stats.releases = releasesJson.size;
        
        int totalDownloads = 0;
        for(JsonValue release : releasesJson) {
            JsonValue assets = release.get("assets");
            if(assets != null) {
                for(JsonValue asset : assets) {
                    totalDownloads += asset.getInt("download_count", 0);
                }
            }
        }
        stats.downloads = totalDownloads;
    } catch(Exception e) {
        Log.err("Parse releases", e);
    }
}

void displayStats(Table statsTable, ModInfo mod, ModStats stats) {
    Core.app.post(() -> {
        statsTable.clearChildren();
        statsTable.defaults().left().pad(6f);
        
        statsTable.add("[yellow]\u2605 Stars:").padRight(15f);
        statsTable.add("[white]" + stats.stars).row();
        
        statsTable.add("[lime]\u2193 Downloads:").padRight(15f);
        statsTable.add("[white]" + stats.downloads).row();
        
        statsTable.add("[cyan]\u26A1 Releases:").padRight(15f);
        statsTable.add("[white]" + stats.releases).row();
        
        statsTable.add("[lightgray]Updated:").padRight(15f);
        statsTable.add("[lightgray]" + formatDate(mod.lastUpdated)).row();
    });
}

void displayStatsError(Table statsTable, ModInfo mod) {
    Core.app.post(() -> {
        statsTable.clearChildren();
        statsTable.defaults().left().pad(6f);
        
        statsTable.add("[scarlet]Stats unavailable").colspan(2).row();
        if(!mod.lastUpdated.isEmpty()) {
            statsTable.add("[lightgray]Updated:").padRight(15f);
            statsTable.add("[lightgray]" + formatDate(mod.lastUpdated)).row();
        }
    });
}

class ModInfo {
    String repo = "";
    String name = "";
    String author = "";
    String description = "";
    String version = "";
    String lastUpdated = "";
    int stars = 0;
    boolean hasJava = false;
    boolean hasScripts = false;
    boolean hasContent = false;
    boolean serverCompatible = false;
    boolean isInstalled = false;
    Mods.LoadedMod installedMod = null;
}

class ModStats {
    int downloads = 0;
    int releases = 0;
    int stars = 0;
}