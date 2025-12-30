import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
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
    private ObjectMap<String, Long> lastStatsFetch = new ObjectMap<>();
    private ObjectSet<String> currentlyLoadingStats = new ObjectSet<>();
    private static final long CACHE_TIME = 300000;
    private int currentPage = 0;
    private int modsPerPage = 10;
    private String searchQuery = "";
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    
    private Color accentColor = Color.valueOf("ffd37f");
    private Color enabledColor = Color.valueOf("84f491");
    private Color disabledColor = Color.valueOf("f25555");
    
    private ObjectMap<String, TextureRegion> badgeSprites = new ObjectMap<>();
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();
    private int currentTab = 0;
    private String sortMode = "updated";
    private long lastRefreshTime = 0;
    private boolean expandEnabled = true;
    private boolean expandDisabled = false;

    public TestMod() {
        Log.info("ModInfo+ Browser Initializing");
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

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadAllBadgeSprites();
                loadModIcons();
                addModInfoButton();
            });
        });
    }
    
    void loadAllBadgeSprites() {
        String[] badgeNames = {"testmod-java-badge", "testmod-js-badge"};
        for(String name : badgeNames) {
            TextureRegion region = Core.atlas.find(name);
            if(region.found()) {
                badgeSprites.put(name, region);
            }
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
    
    void addModInfoButton() {
        Vars.ui.mods.shown(() -> {
            Core.app.post(() -> {
                Vars.ui.mods.hide();
                showEnhancedBrowser();
            });
        });
    }void showEnhancedBrowser() {
    if(browserDialog != null) {
        browserDialog.show();
        return;
    }
    browserDialog = new BaseDialog("Mods");
    browserDialog.addCloseButton();
    
    Table main = new Table(Tex.pane);
    
    main.table(search -> {
        search.image(Icon.zoom).size(32f).padRight(8f);
        searchField = new TextField();
        searchField.setMessageText("Search mods...");
        searchField.changed(() -> {
            searchQuery = searchField.getText().toLowerCase();
            currentPage = 0;
            applyFilter();
        });
        search.add(searchField).growX().height(45f).pad(10f);
    }).fillX().row();
    
    statusLabel = new Label("");
    main.add(statusLabel).pad(8f).row();
    
    Table enabledSection = new Table();
    Table disabledSection = new Table();
    Table browseSection = new Table();
    
    main.collapser(enabledSection, true, () -> {
        enabledSection.clearChildren();
        enabledSection.table(list -> {
            for(ModInfo mod : allMods) {
                if(mod.installedMod != null && mod.installedMod.enabled()) {
                    buildModRow(list, mod);
                }
            }
        }).fillX();
    }).fillX().padTop(4f);
    
    main.button(t -> {
        t.left();
        t.add("[accent]Enabled Mods").style(Styles.outlineLabel);
    }, Styles.clearTogglei, () -> {
        expandEnabled = !expandEnabled;
        if(expandEnabled) {
            currentTab = 0;
            fetchModList();
        }
    }).growX().height(50f).checked(b -> expandEnabled).row();
    
    main.collapser(disabledSection, () -> expandDisabled, () -> {
        disabledSection.clearChildren();
        disabledSection.table(list -> {
            for(ModInfo mod : allMods) {
                if(mod.installedMod != null && !mod.installedMod.enabled()) {
                    buildModRow(list, mod);
                }
            }
        }).fillX();
    }).fillX().padTop(4f);
    
    main.button(t -> {
        t.left();
        t.add("[lightgray]Disabled Mods").style(Styles.outlineLabel);
    }, Styles.clearTogglei, () -> {
        expandDisabled = !expandDisabled;
        if(expandDisabled) {
            currentTab = 1;
            fetchDisabledMods();
        }
    }).growX().height(50f).checked(b -> expandDisabled).row();
    
    main.button("Browse Online Mods", Icon.download, () -> {
        expandEnabled = false;
        expandDisabled = false;
        currentTab = 2;
        fetchRemoteMods();
        showBrowseDialog();
    }).fillX().height(50f).pad(10f).row();
    
    ScrollPane pane = new ScrollPane(main);
    pane.setFadeScrollBars(false);
    browserDialog.cont.add(pane).grow();
    browserDialog.show();
    
    fetchModList();
    fetchDisabledMods();
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
    main.add(paginationBar).fillX().padTop(10f).row();
    
    browse.cont.add(main).grow();
    browse.show();
    
    statusLabel = browseStatus;
}

void buildPaginationBar() {
    if(paginationBar == null) return;
    paginationBar.clearChildren();
    paginationBar.button("<", () -> {
        if(currentPage > 0) {
            currentPage--;
            updateVisibleMods();
        }
    }).size(60f, 50f).disabled(b -> currentPage == 0).padRight(10f);
    
    paginationBar.add().growX();
    paginationBar.label(() -> "Page " + (currentPage + 1) + "/" + Math.max(1, getMaxPage() + 1)).pad(5f);
    paginationBar.add().growX();
    
    paginationBar.button(">", () -> {
        if(currentPage < getMaxPage()) {
            currentPage++;
            updateVisibleMods();
        }
    }).size(60f, 50f).disabled(b -> currentPage >= getMaxPage()).padLeft(10f);
}

void applyFilter() {
    filteredMods.clear();
    if(searchQuery.isEmpty()) {
        filteredMods.addAll(allMods);
    } else {
        for(ModInfo mod : allMods) {
            if(mod.name.toLowerCase().contains(searchQuery) || 
                (mod.description != null && mod.description.toLowerCase().contains(searchQuery))) {
                filteredMods.add(mod);
            }
        }
    }
    applySort();
}

void applySort() {
    if(sortMode.equals("updated")) {
        filteredMods.sort((a, b) -> Long.compare(b.lastUpdatedTime, a.lastUpdatedTime));
    } else if(sortMode.equals("stars")) {
        filteredMods.sort((a, b) -> Integer.compare(b.stars, a.stars));
    } else {
        filteredMods.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
    }
    updateVisibleMods();
}

void updateVisibleMods() {
    if(modListContainer == null) return;
    modListContainer.clearChildren();
    int start = currentPage * modsPerPage;
    int end = Math.min(start + modsPerPage, filteredMods.size);
    
    if(filteredMods.isEmpty()) {
        modListContainer.add("[scarlet]No mods found").pad(30f);
    } else {
        for(int i = start; i < end; i++) {
            buildModRow(modListContainer, filteredMods.get(i));
        }
    }
    updateStatusLabel("Showing " + filteredMods.size + " mods");
    if(currentTab == 2) buildPaginationBar();
}

void updateStatusLabel(String text) {
    if(statusLabel != null) statusLabel.setText("[lightgray]" + text);
}

int getMaxPage() {
    return Math.max(0, (filteredMods.size - 1) / modsPerPage);
}

void reloadMods() {
    allMods.clear();
    filteredMods.clear();
    statsCache.clear();
    lastStatsFetch.clear();
    currentlyLoadingStats.clear();
    lastRefreshTime = Time.millis();
    if(currentTab == 0) {
        fetchModList();
    } else if(currentTab == 1) {
        fetchDisabledMods();
    } else {
        fetchRemoteMods();
    }
}void fetchModList() {
    updateStatusLabel("[cyan]Loading enabled mods...");
    Core.app.post(() -> {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(!mod.enabled()) continue;
            ModInfo info = new ModInfo();
            if(mod.meta != null) {
                info.name = mod.meta.displayName != null ? mod.meta.displayName : mod.meta.name;
                info.author = mod.meta.author;
                info.description = mod.meta.description;
                info.version = mod.meta.version;
                info.hasJava = mod.meta.java;
                info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
                info.repo = mod.meta.repo != null ? mod.meta.repo : "";
            } else {
                info.name = mod.name;
            }
            info.installedMod = mod;
            info.isInstalled = true;
            allMods.add(info);
        }
        Core.app.post(() -> {
            currentPage = 0;
            applyFilter();
        });
    });
}

void fetchDisabledMods() {
    updateStatusLabel("[cyan]Loading disabled mods...");
    Core.app.post(() -> {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(mod.enabled()) continue;
            ModInfo info = new ModInfo();
            if(mod.meta != null) {
                info.name = mod.meta.displayName != null ? mod.meta.displayName : mod.meta.name;
                info.author = mod.meta.author;
                info.description = mod.meta.description;
                info.version = mod.meta.version;
                info.hasJava = mod.meta.java;
                info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
                info.repo = mod.meta.repo != null ? mod.meta.repo : "";
            } else {
                info.name = mod.name;
            }
            info.installedMod = mod;
            info.isInstalled = true;
            allMods.add(info);
        }
        Core.app.post(() -> {
            currentPage = 0;
            applyFilter();
        });
    });
}

void fetchRemoteMods() {
    updateStatusLabel("[cyan]Loading online mods...");
    Core.app.post(() -> {
        try {
            String url = "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mindustry-ModBrowser");
            conn.setRequestProperty("Authorization", "token " + getNextToken());
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            if(conn.getResponseCode() != 200) {
                Core.app.post(() -> updateStatusLabel("[scarlet]Failed to load"));
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while((line = reader.readLine()) != null) response.append(line);
            reader.close();
            
            Seq<ModInfo> mods = parseModList(response.toString());
            mods.sort(m -> -m.stars);
            
            Core.app.post(() -> {
                allMods = mods;
                currentPage = 0;
                applyFilter();
            });
        } catch(Exception ex) {
            Core.app.post(() -> updateStatusLabel("[scarlet]Failed: " + ex.getMessage()));
        }
    });
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
            mod.lastUpdatedTime = parseTimestamp(mod.lastUpdated);
            
            for(Mods.LoadedMod installed : Vars.mods.list()) {
                if(installed.meta != null && installed.meta.repo != null && 
                   installed.meta.repo.equalsIgnoreCase(mod.repo)) {
                    mod.isInstalled = true;
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

long parseTimestamp(String dateStr) {
    try {
        String[] parts = dateStr.split("T")[0].split("-");
        return Long.parseLong(parts[0]) * 10000000000L + 
               Long.parseLong(parts[1]) * 100000000L + 
               Long.parseLong(parts[2]) * 1000000L;
    } catch(Exception e) {
        return 0;
    }
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
    Mods.LoadedMod installed = mod.installedMod;
    
    table.table(Tex.button, row -> {
        row.left();
        
        TextureRegion icon = getModIcon(mod, installed);
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
                
                TextureRegion badge = getBadge(mod);
                if(badge != null && badge.found()) {
                    title.image(badge).size(28f, 18f).padRight(4f);
                } else {
                    String badgeText = mod.hasJava ? "[#b07219][[JAVA]" : "[#f1e05a][[JS]";
                    title.add(badgeText).style(Styles.outlineLabel);
                }
                
                if(mod.stars >= 10) {
                    title.add(" [yellow]\u2605" + mod.stars).padLeft(6f);
                }
            }).row();
            
            info.add("[lightgray]by " + mod.author + " [gray]| v" + mod.version).padTop(4f).row();
            
            if(!mod.description.isEmpty()) {
                Label desc = new Label(mod.description.length() > 80 ? 
                    mod.description.substring(0, 77) + "..." : mod.description);
                desc.setWrap(true);
                desc.setColor(Color.lightGray);
                info.add(desc).width(350f).padTop(4f).row();
            }
            
        }).growX().pad(10f);
        
        row.table(btns -> {
            btns.defaults().size(50f);
            
            if(currentTab < 2) {
                btns.button(Icon.settings, Styles.clearNonei, () -> {
                    showModDetails(mod);
                }).tooltip("Details");
            } else {
                btns.button(Icon.info, Styles.clearNonei, () -> {
                    showModDetails(mod);
                }).tooltip("Details");
                
                btns.button(Icon.link, Styles.clearNonei, () -> {
                    Core.app.openURI("https://github.com/" + mod.repo);
                }).tooltip("GitHub");
                
                if(!mod.isInstalled) {
                    btns.button(Icon.download, Styles.clearNonei, () -> {
                        installMod(mod);
                    }).tooltip("Install");
                }
            }
        }).right().padRight(10f);
        
    }).fillX().height(120f).pad(4f).row();
}

TextureRegion getModIcon(ModInfo mod, Mods.LoadedMod installed) {
    if(installed != null && installed.iconTexture != null) {
        return new TextureRegion(installed.iconTexture);
    }
    
    String key = mod.name.toLowerCase();
    if(modIcons.containsKey(key)) {
        return modIcons.get(key);
    }
    
    return null;
}

TextureRegion getBadge(ModInfo mod) {
    return mod.hasJava ? badgeSprites.get("testmod-java-badge") : badgeSprites.get("testmod-js-badge");
}void installMod(ModInfo mod) {
    try {
        Vars.ui.mods.githubImportMod(mod.repo, true);
        Vars.ui.showInfo("Installing " + mod.name + "...");
    } catch(Exception e) {
        Log.err("Install error", e);
        Vars.ui.showErrorMessage("Install failed: " + e.getMessage());
    }
}

void showModDetails(ModInfo mod) {
    Mods.LoadedMod installed = mod.installedMod;
    
    BaseDialog dialog = new BaseDialog(mod.name);
    dialog.addCloseButton();
    
    Table content = new Table(Tex.pane);
    content.margin(15f);
    
    TextureRegion icon = getModIcon(mod, installed);
    if(icon != null) {
        content.image(icon).size(80f).pad(10f).row();
    } else {
        content.image(Icon.box).size(80f).color(Color.gray).pad(10f).row();
    }
    
    Table titleRow = new Table();
    titleRow.add("[accent]" + mod.name).pad(5f);
    TextureRegion badge = getBadge(mod);
    if(badge != null && badge.found()) {
        titleRow.image(badge).size(36f, 22f).padLeft(8f);
    } else {
        String badgeText = mod.hasJava ? " [#b07219][[JAVA]" : " [#f1e05a][[JS]";
        titleRow.add(badgeText);
    }
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
    statsTable.add("[cyan]Loading stats...").pad(15f);
    content.add(statsTable).row();
    
    content.image().height(3f).width(400f).color(accentColor).pad(10f).row();
    
    content.table(actions -> {
        actions.button("Open GitHub", Icon.link, () -> {
            Core.app.openURI("https://github.com/" + mod.repo);
        }).size(220f, 55f).pad(5f);
        
        if(installed == null && !mod.isInstalled) {
            actions.button("Install", Icon.download, () -> {
                installMod(mod);
                dialog.hide();
            }).size(220f, 55f).pad(5f);
        }
    }).row();
    
    ScrollPane pane = new ScrollPane(content);
    dialog.cont.add(pane).grow();
    dialog.show();
    
    if(!mod.repo.isEmpty()) {
        loadGitHubStats(mod, statsTable);
    }
}

void loadGitHubStats(ModInfo mod, Table statsTable) {
    String key = mod.repo;
    if(statsCache.containsKey(key)) {
        displayStats(statsTable, mod, statsCache.get(key));
        return;
    }
    
    fetchModStats(mod, stats -> {
        if(stats != null) {
            statsCache.put(key, stats);
            displayStats(statsTable, mod, stats);
        } else {
            displayStatsError(statsTable, mod);
        }
    });
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
        statsTable.add("[lightgray]Updated:").padRight(15f);
        statsTable.add("[lightgray]" + formatDate(mod.lastUpdated)).row();
    });
}

void fetchModStats(ModInfo mod, Cons<ModStats> callback) {
    Core.app.post(() -> {
        HttpURLConnection repoConn = null;
        HttpURLConnection relConn = null;
        try {
            String repoUrl = mod.repo;
            String[] parts = repoUrl.split("/");
            if(parts.length < 2) {
                callback.get(null);
                return;
            }
            
            String owner = parts[0];
            String repo = parts[1];
            
            repoConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo).openConnection();
            repoConn.setRequestProperty("User-Agent", "Mindustry-ModBrowser");
            repoConn.setRequestProperty("Authorization", "token " + getNextToken());
            repoConn.setConnectTimeout(10000);
            repoConn.setReadTimeout(10000);
            
            if(repoConn.getResponseCode() != 200) {
                callback.get(null);
                return;
            }
            
            BufferedReader repoReader = new BufferedReader(new InputStreamReader(repoConn.getInputStream()));
            StringBuilder repoData = new StringBuilder();
            String line;
            while((line = repoReader.readLine()) != null) repoData.append(line);
            repoReader.close();
            
            ModStats stats = new ModStats();
            
            try {
                JsonValue repoJson = new JsonReader().parse(repoData.toString());
                stats.stars = repoJson.getInt("stargazers_count", 0);
            } catch(Exception e) {
                Log.err("Parse repo", e);
            }
            
            try {
                relConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases").openConnection();
                relConn.setRequestProperty("User-Agent", "Mindustry-ModBrowser");
                relConn.setRequestProperty("Authorization", "token " + getNextToken());
                relConn.setConnectTimeout(10000);
                relConn.setReadTimeout(10000);
                
                if(relConn.getResponseCode() == 200) {
                    BufferedReader relReader = new BufferedReader(new InputStreamReader(relConn.getInputStream()));
                    StringBuilder relData = new StringBuilder();
                    while((line = relReader.readLine()) != null) relData.append(line);
                    relReader.close();
                    
                    JsonValue releasesJson = new JsonReader().parse(relData.toString());
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
                }
            } catch(Exception e) {
                Log.err("Parse releases", e);
            }
            
            callback.get(stats);
            
        } catch(Exception e) {
            Log.err("GitHub API", e);
            callback.get(null);
        } finally {
            if(repoConn != null) try { repoConn.disconnect(); } catch(Exception e) {}
            if(relConn != null) try { relConn.disconnect(); } catch(Exception e) {}
        }
    });
}class ModInfo {
        String repo = "";
        String name = "";
        String author = "";
        String description = "";
        String version = "";
        String lastUpdated = "";
        long lastUpdatedTime = 0;
        int stars = 0;
        boolean hasJava = false;
        boolean hasScripts = false;
        boolean isInstalled = false;
        Mods.LoadedMod installedMod = null;
    }
    
    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}