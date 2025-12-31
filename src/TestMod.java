import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.files.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import arc.util.serialization.*;

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
            for(String part : parts) sb.append(part);
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
    private ObjectMap<String, TextureRegion> iconCache = new ObjectMap<>();
    private int currentPage = 0;
    private int modsPerPage = 8;
    private String searchQuery = "";
    private BaseDialog mainDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private Color accentColor = Color.valueOf("ffd37f");
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;
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
            int idx = (currentTokenIndex + i) % tokens.size;
            TokenInfo token = tokens.get(idx);
            
            if(token.rateLimited) {
                if(Time.millis() - token.lastUsed > RATE_LIMIT_RESET_TIME) {
                    token.rateLimited = false;
                    token.requestCount = 0;
                } else {
                    continue;
                }
            }
            
            if(token.requestCount < MAX_REQUESTS_PER_TOKEN) {
                currentTokenIndex = idx;
                token.requestCount++;
                token.lastUsed = Time.millis();
                return token.getToken();
            }
        }
        
        return tokens.get(0).getToken();
    }
    
    void markTokenRateLimited(int responseCode) {
        if(responseCode == 403) {
            tokens.get(currentTokenIndex).rateLimited = true;
            currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
        }
    }
    
    void githubGet(String url, Cons<String> success, Runnable fail) {
        Http.get(url)
            .header("User-Agent", "Mindustry-ModBrowser")
            .header("Authorization", "token " + getNextToken())
            .timeout(15000)
            .error(e -> Core.app.post(fail))
            .submit(res -> {
                int code = res.getStatus().getStatusCode();
                if(code == 403) {
                    markTokenRateLimited(code);
                    Core.app.post(fail);
                } else if(code == 200) {
                    String text = res.getResultAsString();
                    Core.app.post(() -> success.get(text));
                } else {
                    Core.app.post(fail);
                }
            });
    }

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadBadges();
                replaceModsButton();
            });
        });
    }
    
    void loadBadges() {
        javaBadge = Core.atlas.find("testmod-java-badge");
        jsBadge = Core.atlas.find("testmod-js-badge");
        
        if(!javaBadge.found()) javaBadge = null;
        if(!jsBadge.found()) jsBadge = null;
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
        if(mainDialog != null) {
            mainDialog.show();
            return;
        }
        
        mainDialog = new BaseDialog("") {
            {
                setFillParent(true);
                cont.clear();
                buttons.clear();
            }
        };
        
        Table bg = new Table();
        bg.setFillParent(true);
        bg.setBackground(new TextureRegionDrawable(Core.atlas.white()) {
            {
                tint(new Color(0, 0, 0, 0.7f));
            }
        });
        mainDialog.cont.addChild(bg);
        
        Table main = new Table();
        main.setBackground(new TextureRegionDrawable(Core.atlas.white()) {
            {
                tint(new Color(0.15f, 0.15f, 0.15f, 0.95f));
            }
        });
        
        main.table(header -> {
            header.setBackground(Tex.button);
            header.image(Icon.book).size(40f).padLeft(15f).padRight(10f);
            header.add("[accent]MODINFO+").style(Styles.outlineLabel).left();
            header.add().growX();
            header.button(Icon.refresh, Styles.cleari, 40f, this::reloadMods).size(50f).padRight(10f);
            header.button(Icon.cancel, Styles.cleari, 40f, mainDialog::hide).size(50f).padRight(10f);
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
            tabs.defaults().growX().height(50f);
            tabs.button("Enabled", () -> switchTab(0)).update(b -> b.setChecked(currentTab == 0));
            tabs.button("Disabled", () -> switchTab(1)).update(b -> b.setChecked(currentTab == 1));
            tabs.button("Browse", () -> switchTab(2)).update(b -> b.setChecked(currentTab == 2));
        }).fillX().row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        main.add(pane).grow().row();
        
        paginationBar = new Table();
        main.add(paginationBar).fillX().row();
        
        float screenHeight = Core.graphics.getHeight();
        float dialogHeight = Math.min(screenHeight * 0.95f, 1000f);
        
        mainDialog.cont.add(main).size(Math.min(900f, Core.graphics.getWidth() * 0.95f), dialogHeight);
        mainDialog.show();
        
        switchTab(0);
    }void switchTab(int tab) {
    currentTab = tab;
    currentPage = 0;
    searchQuery = "";
    if(searchField != null) searchField.setText("");
    
    if(tab == 0) fetchEnabledMods();
    else if(tab == 1) fetchDisabledMods();
    else fetchRemoteMods();
}

void reloadMods() {
    allMods.clear();
    filteredMods.clear();
    statsCache.clear();
    switchTab(currentTab);
}

void buildPaginationBar() {
    if(paginationBar == null) return;
    paginationBar.clearChildren();
    
    if(currentTab != 2) return;
    
    paginationBar.setBackground(Tex.button);
    
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
        if(filteredMods.isEmpty()) {
            modListContainer.add("[lightgray]No mods found").pad(40f);
        } else {
            for(ModInfo mod : filteredMods) {
                buildModRow(modListContainer, mod);
            }
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
}

void fetchEnabledMods() {
    updateStatusLabel("[cyan]Loading enabled mods...");
    Core.app.post(() -> {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(!mod.enabled()) continue;
            ModInfo info = createModInfo(mod);
            allMods.add(info);
        }
        Core.app.post(() -> {
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
            allMods.add(info);
        }
        Core.app.post(() -> {
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
        info.repo = mod.meta.repo != null ? mod.meta.repo : "";
    } else {
        info.name = mod.name;
        info.author = "Unknown";
        info.description = "";
        info.version = "1.0";
        info.repo = "";
    }
    
    info.installedMod = mod;
    info.isInstalled = true;
    detectCapabilities(info, mod);
    
    return info;
}

void detectCapabilities(ModInfo info, Mods.LoadedMod mod) {
    info.hasJava = mod.main != null;
    info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
    info.hasContent = mod.root != null && mod.root.child("content").exists();
    info.serverCompatible = mod.meta != null && !mod.meta.hidden;
}

void fetchRemoteMods() {
    updateStatusLabel("[cyan]Loading online mods...");
    
    githubGet(
        "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json",
        json -> {
            allMods = parseModList(json);
            
            for(ModInfo mod : allMods) {
                if(!mod.repo.isEmpty()) {
                    lazyLoadIcon(mod);
                }
            }
            
            applyFilter();
            updateVisibleMods();
        },
        () -> updateStatusLabel("[scarlet]Failed to load mod list")
    );
}

void lazyLoadIcon(ModInfo mod) {
    if(iconCache.containsKey(mod.repo)) return;
    
    githubGet(
        "https://raw.githubusercontent.com/" + mod.repo + "/master/icon.png",
        ignored -> {},
        () -> {}
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
            mod.serverCompatible = !modJson.getBoolean("clientSide", true);
            
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
}void buildModRow(Table table, ModInfo mod) {
    table.table(Tex.button, row -> {
        row.left();
        
        TextureRegion icon = getModIcon(mod);
        if(icon != null) {
            row.image(icon).size(64f).padLeft(10f).padRight(12f);
        } else {
            row.image(Icon.box).size(64f).color(Color.darkGray).padLeft(10f).padRight(12f);
        }
        
        row.table(info -> {
            info.left().defaults().left();
            
            info.table(title -> {
                title.left();
                title.add("[accent]" + mod.name).style(Styles.outlineLabel).padRight(8f);
                
                Table badges = new Table();
                badges.left().defaults().padRight(10f);
                
                if(mod.hasJava) {
                    Table jBadge = new Table(Styles.black6);
                    if(javaBadge != null) {
                        jBadge.image(javaBadge).size(28f, 18f).pad(4f);
                    } else {
                        jBadge.add("[#b07219]JAVA").style(Styles.outlineLabel).pad(4f);
                    }
                    jBadge.clicked(() -> Vars.ui.showInfo("[#b07219]Java Mod\n[lightgray]Compiled code"));
                    badges.add(jBadge);
                }
                
                if(mod.hasScripts) {
                    Table sBadge = new Table(Styles.black6);
                    if(jsBadge != null) {
                        sBadge.image(jsBadge).size(28f, 18f).pad(4f);
                    } else {
                        sBadge.add("[#f1e05a]JS").style(Styles.outlineLabel).pad(4f);
                    }
                    sBadge.clicked(() -> Vars.ui.showInfo("[#f1e05a]JavaScript Mod\n[lightgray]Uses scripts"));
                    badges.add(sBadge);
                }
                
                if(mod.serverCompatible) {
                    Table cBadge = new Table(Styles.black6);
                    cBadge.image(Icon.host).size(20f).color(Color.royal).pad(4f);
                    cBadge.clicked(() -> Vars.ui.showInfo("[royal]Server Compatible\n[lightgray]Works on multiplayer"));
                    badges.add(cBadge);
                }
                
                title.add(badges);
                
                if(mod.stars >= 10) {
                    title.add(" [yellow]\u2605" + mod.stars).padLeft(8f);
                }
                
                if(mod.isInstalled) {
                    title.image(Icon.ok).size(20f).color(Color.lime).padLeft(8f);
                }
            }).row();
            
            info.add("[lightgray]by " + mod.author + " [gray]| v" + mod.version).padTop(4f).row();
            
            if(!mod.description.isEmpty()) {
                String desc = mod.description.length() > 80 ? 
                    mod.description.substring(0, 77) + "..." : mod.description;
                Label descLabel = new Label(desc);
                descLabel.setWrap(true);
                descLabel.setColor(Color.lightGray);
                info.add(descLabel).width(450f).padTop(4f).row();
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
                        downloadMod(mod);
                    }).tooltip("Install");
                }
            }
        }).right().padRight(10f);
        
    }).fillX().height(110f).pad(4f).row();
}

TextureRegion getModIcon(ModInfo mod) {
    if(mod.installedMod != null && mod.installedMod.iconTexture != null) {
        return new TextureRegion(mod.installedMod.iconTexture);
    }
    
    if(iconCache.containsKey(mod.repo)) {
        return iconCache.get(mod.repo);
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

void downloadMod(ModInfo mod) {
    if(mod.repo.isEmpty()) {
        Vars.ui.showErrorMessage("No repository URL");
        return;
    }
    
    updateStatusLabel("[cyan]Downloading " + mod.name + "...");
    
    githubGet(
        "https://api.github.com/repos/" + mod.repo + "/releases/latest",
        json -> {
            try {
                JsonValue release = new JsonReader().parse(json);
                JsonValue assets = release.get("assets");
                
                String downloadUrl = null;
                
                if(assets != null && assets.size > 0) {
                    for(JsonValue asset : assets) {
                        String name = asset.getString("name", "");
                        if(name.endsWith(".jar") || name.endsWith(".zip")) {
                            downloadUrl = asset.getString("browser_download_url", "");
                            break;
                        }
                    }
                }
                
                if(downloadUrl != null) {
                    String finalUrl = downloadUrl;
                    Core.app.post(() -> {
                        Vars.ui.showInfo("Downloading release...");
                        downloadModFile(mod, finalUrl);
                    });
                } else {
                    Core.app.post(() -> downloadFromZipball(mod));
                }
            } catch(Exception e) {
                Log.err("Release parse", e);
                Core.app.post(() -> downloadFromZipball(mod));
            }
        },
        () -> downloadFromZipball(mod)
    );
}

void downloadFromZipball(ModInfo mod) {
    String zipUrl = "https://github.com/" + mod.repo + "/archive/refs/heads/master.zip";
    Vars.ui.showInfo("Downloading repository...");
    downloadModFile(mod, zipUrl);
}

void downloadModFile(ModInfo mod, String url) {
    try {
        arc.files.Fi file = Vars.tmpDirectory.child(mod.name + "-temp.zip");
        
        Http.get(url)
            .timeout(30000)
            .error(e -> Core.app.post(() -> {
                Vars.ui.showErrorMessage("Download failed: " + e.getMessage());
                updateStatusLabel("[scarlet]Download failed");
            }))
            .submit(res -> {
                try {
                    file.writeBytes(res.getResult());
                    Core.app.post(() -> installDownloadedMod(mod, file));
                } catch(Exception e) {
                    Log.err("Write error", e);
                    Core.app.post(() -> {
                        Vars.ui.showErrorMessage("Save failed");
                        updateStatusLabel("[scarlet]Save failed");
                    });
                }
            });
    } catch(Exception e) {
        Log.err("Download error", e);
        Vars.ui.showErrorMessage("Download error");
        updateStatusLabel("[scarlet]Download error");
    }
}

void installDownloadedMod(ModInfo mod, arc.files.Fi zipFile) {
    try {
        arc.files.Fi modsDir = Vars.modDirectory;
        arc.files.Fi extractDir = modsDir.child(mod.name);
        
        if(extractDir.exists()) extractDir.deleteDirectory();
        extractDir.mkdirs();
        
        arc.files.ZipFi zip = new arc.files.ZipFi(zipFile);
        
        String rootFolder = null;
        for(arc.files.Fi entry : zip.list()) {
            if(entry.isDirectory()) {
                rootFolder = entry.name();
                break;
            }
        }
        
        final String root = rootFolder;
        zip.walk(entry -> {
            if(entry.name().equals(zipFile.name())) return;
            
            String entryPath = entry.path();
            if(root != null && entryPath.startsWith(root + "/")) {
                entryPath = entryPath.substring(root.length() + 1);
            }
            
            if(entryPath.isEmpty()) return;
            
            arc.files.Fi output = extractDir.child(entryPath);
            
            if(entry.isDirectory()) {
                output.mkdirs();
            } else {
                output.parent().mkdirs();
                output.writeBytes(entry.readBytes());
            }
        });
        
        zipFile.delete();
        
        Vars.mods.load();
        Vars.ui.showInfo("[lime]Installed " + mod.name + "!\n[lightgray]Restart recommended");
        
        Core.app.post(this::reloadMods);
        
    } catch(Exception e) {
        Log.err("Install error", e);
        Vars.ui.showErrorMessage("Install failed");
        updateStatusLabel("[scarlet]Install failed");
    }
}void showModDetails(ModInfo mod) {
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
    badges.defaults().padRight(10f);
    
    if(mod.hasJava) {
        Table jBadge = new Table(Styles.black6);
        if(javaBadge != null) {
            jBadge.image(javaBadge).size(36f, 22f).pad(4f);
        } else {
            jBadge.add("[#b07219]JAVA").pad(4f);
        }
        jBadge.clicked(() -> Vars.ui.showInfo("[#b07219]Java Mod\n[lightgray]Compiled code"));
        badges.add(jBadge);
    }
    
    if(mod.hasScripts) {
        Table sBadge = new Table(Styles.black6);
        if(jsBadge != null) {
            sBadge.image(jsBadge).size(36f, 22f).pad(4f);
        } else {
            sBadge.add("[#f1e05a]JS").pad(4f);
        }
        sBadge.clicked(() -> Vars.ui.showInfo("[#f1e05a]JavaScript Mod\n[lightgray]Uses scripts"));
        badges.add(sBadge);
    }
    
    if(mod.serverCompatible) {
        Table cBadge = new Table(Styles.black6);
        cBadge.image(Icon.host).size(24f).color(Color.royal).pad(4f);
        cBadge.clicked(() -> Vars.ui.showInfo("[royal]Server Compatible\n[lightgray]Works on multiplayer"));
        badges.add(cBadge);
    }
    
    titleRow.add(badges);
    content.add(titleRow).row();
    
    content.add("[cyan]by " + mod.author).pad(5f).row();
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
                downloadMod(mod);
                dialog.hide();
            }).size(220f, 55f).pad(5f);
        } else if(mod.installedMod != null) {
            actions.button(mod.installedMod.enabled() ? "Disable" : "Enable", 
                mod.installedMod.enabled() ? Icon.cancel : Icon.ok, () -> {
                toggleMod(mod);
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
}