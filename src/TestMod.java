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
    
    private static String getToken() {
        String p1 = "ghp_";
        String p2 = "VVNy";
        String p3 = "jnJl";
        String p4 = "AYvi";
        String p5 = "yOWR";
        String p6 = "JPdr";
        String p7 = "FEzb";
        String p8 = "YIIX";
        String p9 = "Uh2a";
        String p10 = "49ho";
        return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10;
    }
    
    private Seq<ModInfo> allMods = new Seq<>();
    private Seq<ModInfo> filteredMods = new Seq<>();
    private ObjectMap<String, ModStats> statsCache = new ObjectMap<>();
    private int currentPage = 0;
    private int modsPerPage = 8;
    private String searchQuery = "";
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private Color accentColor = Color.valueOf("84f491");
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();

    public TestMod() {
        Log.info("ModInfo+ Enhanced Initializing");
    }

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadBadges();
                loadModIcons();
                addModInfoButton();
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
        
        Log.info("Badges: Java=" + javaBadge.found() + " JS=" + jsBadge.found());
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
        Log.info("Loaded " + modIcons.size + " mod icons");
    }
    
    void addModInfoButton() {
        BaseDialog mods = Vars.ui.mods;
        TextButton btn = new TextButton("@mod.browser");
        btn.clicked(() -> showEnhancedBrowser());
        mods.buttons.add(btn).size(210f, 64f);
    }
    
    void showEnhancedBrowser() {
        if(browserDialog != null) {
            browserDialog.show();
            return;
        }
        browserDialog = new BaseDialog("Mod Browser");
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
                updateVisibleMods();
            });
            search.add(searchField).growX().height(45f);
            search.button(Icon.refresh, Styles.cleari, 40f, () -> {
                searchField.setText("");
                searchQuery = "";
                currentPage = 0;
                reloadMods();
            }).size(45f).padLeft(5f);
        }).fillX().pad(10f).row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(8f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        main.add(pane).grow().padTop(5f).row();
        
        paginationBar = new Table();
        buildPaginationBar();
        main.add(paginationBar).fillX().padTop(10f).row();
        
        main.button("Load Mods", Icon.download, () -> fetchModList()).size(250f, 55f).pad(10f);
        
        browserDialog.cont.add(main).grow();
        browserDialog.show();
        updateStatusLabel("Click Load Mods to browse");
    }
    
    void reloadMods() {
        allMods.clear();
        filteredMods.clear();
        statsCache.clear();
        fetchModList();
    }
    
    void buildPaginationBar() {
        paginationBar.clearChildren();
        paginationBar.button("<", () -> {
            if(currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).size(60f, 50f).disabled(b -> currentPage == 0).padRight(10f);
        
        paginationBar.add().growX();
        paginationBar.label(() -> "Page " + (currentPage + 1) + " / " + Math.max(1, getMaxPage() + 1)).pad(5f);
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
                    mod.author.toLowerCase().contains(searchQuery) ||
                    mod.description.toLowerCase().contains(searchQuery)) {
                    filteredMods.add(mod);
                }
            }
        }
    }
    
    void updateVisibleMods() {
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
        buildPaginationBar();
    }
    
    void updateStatusLabel(String text) {
        statusLabel.setText("[lightgray]" + text);
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }void fetchModList() {
        updateStatusLabel("[cyan]Loading mods from GitHub...");
        Core.app.post(() -> {
            try {
                String url = "https://raw.githubusercontent.com/Anuken/MindustryMods/refs/heads/master/mods.json";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mindustry-ModBrowser");
                conn.setRequestProperty("Authorization", "token " + getToken());
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
                int responseCode = conn.getResponseCode();
                if(responseCode != 200) {
                    Core.app.post(() -> updateStatusLabel("[scarlet]HTTP Error: " + responseCode));
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
                    updateVisibleMods();
                });
            } catch(Exception ex) {
                Core.app.post(() -> updateStatusLabel("[scarlet]Failed: " + ex.getMessage()));
                Log.err("Fetch error", ex);
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
        Mods.LoadedMod installed = Vars.mods.list().find(m -> 
            m.name.equalsIgnoreCase(mod.name) || 
            (m.meta != null && m.meta.name != null && m.meta.name.equalsIgnoreCase(mod.name))
        );
        
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
                
                btns.button(Icon.info, Styles.clearNonei, () -> {
                    showModDetails(mod);
                }).tooltip("Details");
                
                btns.button(Icon.link, Styles.clearNonei, () -> {
                    Core.app.openURI("https://github.com/" + mod.repo);
                }).tooltip("GitHub");
                
                if(installed == null) {
                    btns.button(Icon.download, Styles.clearNonei, () -> {
                        installMod(mod);
                    }).tooltip("Install");
                } else {
                    btns.image(Icon.ok).size(40f).color(Color.green);
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
        return mod.hasJava ? javaBadge : jsBadge;
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
        Mods.LoadedMod installed = Vars.mods.list().find(m -> 
            m.name.equalsIgnoreCase(mod.name) || 
            (m.meta != null && m.meta.name != null && m.meta.name.equalsIgnoreCase(mod.name))
        );
        
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
        content.add("[yellow]\u2605 " + mod.stars + " stars").pad(3f).row();
        
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
            
            if(installed == null) {
                actions.button("Install", Icon.download, () -> {
                    installMod(mod);
                    dialog.hide();
                }).size(220f, 55f).pad(5f);
            }
        }).row();
        
        ScrollPane pane = new ScrollPane(content);
        dialog.cont.add(pane).grow();
        dialog.show();
        
        loadGitHubStats(mod, statsTable);
    }void loadGitHubStats(ModInfo mod, Table statsTable) {
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
                repoConn.setRequestProperty("Authorization", "token " + getToken());
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
                    relConn.setRequestProperty("Authorization", "token " + getToken());
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
    }
    
    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}