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

    public TestMod() {
        Log.info("ModInfo+ Initializing");
    }

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadBadges();
                addModInfoButton();
            });
        });
    }
    
    void loadBadges() {
        javaBadge = Core.atlas.find("testmod-java-badge");
        jsBadge = Core.atlas.find("testmod-js-badge");
        Log.info("Badge load: Java=" + javaBadge.found() + " JS=" + jsBadge.found());
    }
    
    void addModInfoButton() {
        BaseDialog mods = Vars.ui.mods;
        TextButton btn = new TextButton("@mod.browser");
        btn.clicked(() -> showEnhancedBrowser());
        mods.buttons.add(btn).size(210f, 64f);
    }
    
    void showEnhancedBrowser() {
        if (browserDialog != null) {
            browserDialog.show();
            return;
        }
        browserDialog = new BaseDialog("Mod Browser");
        browserDialog.addCloseButton();
        
        Table main = new Table(Tex.pane);
        
        main.table(search -> {
            search.image(Icon.zoom).size(32f).padRight(8f);
            searchField = new TextField();
            searchField.setMessageText("Search...");
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
                applyFilter();
                updateVisibleMods();
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
        updateStatusLabel("Click Load Mods to start");
    }
    
    void buildPaginationBar() {
        paginationBar.clearChildren();
        paginationBar.button("<", () -> {
            if (currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).size(60f, 50f).disabled(b -> currentPage == 0).padRight(10f);
        
        paginationBar.add().growX();
        paginationBar.label(() -> "Page " + (currentPage + 1) + " / " + (getMaxPage() + 1)).pad(5f);
        paginationBar.add().growX();
        
        paginationBar.button(">", () -> {
            if (currentPage < getMaxPage()) {
                currentPage++;
                updateVisibleMods();
            }
        }).size(60f, 50f).disabled(b -> currentPage >= getMaxPage()).padLeft(10f);
    }
    
    void applyFilter() {
        filteredMods.clear();
        if (searchQuery.isEmpty()) {
            filteredMods.addAll(allMods);
        } else {
            for (ModInfo mod : allMods) {
                if (mod.name.toLowerCase().contains(searchQuery) || 
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
        
        if (filteredMods.isEmpty()) {
            modListContainer.add("[scarlet]No mods found").pad(30f);
        } else {
            for (int i = start; i < end; i++) {
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
    }
    
    void fetchModList() {
        updateStatusLabel("[cyan]Loading mods...");
        Core.app.post(() -> {
            try {
                String url = "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mindustry");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                
                Seq<ModInfo> mods = parseModList(response.toString());
                mods.sort(m -> -m.lastUpdatedTime);
                
                Core.app.post(() -> {
                    allMods = mods;
                    currentPage = 0;
                    applyFilter();
                    updateVisibleMods();
                });
            } catch (Exception ex) {
                Core.app.post(() -> updateStatusLabel("[scarlet]Failed to load: " + ex.getMessage()));
                Log.err("Fetch error", ex);
            }
        });
    }
    
    Seq<ModInfo> parseModList(String json) {
        Seq<ModInfo> mods = new Seq<>();
        try {
            JsonValue root = new JsonReader().parse(json);
            for (JsonValue modJson : root) {
                ModInfo mod = new ModInfo();
                mod.repo = modJson.getString("repo", "");
                mod.name = modJson.getString("name", "Unknown");
                mod.author = modJson.getString("author", "Unknown");
                mod.description = modJson.getString("description", "");
                mod.version = modJson.getString("version", "?");
                mod.lastUpdated = modJson.getString("lastUpdated", "");
                mod.lastUpdatedTime = parseTimestamp(mod.lastUpdated);
                mod.isJava = modJson.getBoolean("java", false);
                
                if (!mod.repo.isEmpty() && !mod.name.isEmpty()) {
                    mods.add(mod);
                }
            }
        } catch (Exception e) {
            Log.err("Parse error", e);
        }
        return mods;
    }
    
    long parseTimestamp(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            if (parts.length >= 3) {
                return Long.parseLong(parts[0]) * 10000000000L + 
                       Long.parseLong(parts[1]) * 100000000L + 
                       Long.parseLong(parts[2].substring(0, 2)) * 1000000L;
            }
        } catch (Exception e) {}
        return 0;
    }
    
    String formatDate(String dateStr) {
        try {
            String[] parts = dateStr.split("T")[0].split("-");
            return parts[1] + "/" + parts[2] + "/" + parts[0];
        } catch (Exception e) {
            return dateStr;
        }
    }

    void buildModRow(Table table, ModInfo mod) {
        Mods.LoadedMod installed = Vars.mods.list().find(m -> m.name.equals(mod.name) || m.meta.name.equals(mod.name));
        
        table.table(Tex.button, row -> {
            row.left();
            
            if (installed != null && installed.iconTexture != null) {
                row.image(new TextureRegion(installed.iconTexture)).size(64f).padLeft(10f).padRight(12f);
            } else {
                row.image(Icon.box).size(64f).padLeft(10f).padRight(12f);
            }
            
            row.table(info -> {
                info.left().defaults().left();
                
                info.table(title -> {
                    title.left();
                    title.add("[accent]" + mod.name).style(Styles.outlineLabel).padRight(8f);
                    
                    if (mod.isJava) {
                        if (javaBadge != null && javaBadge.found()) {
                            title.image(javaBadge).size(28f, 18f);
                        } else {
                            title.add("[[JAVA]").color(Color.valueOf("b07219")).style(Styles.outlineLabel);
                        }
                    } else {
                        if (jsBadge != null && jsBadge.found()) {
                            title.image(jsBadge).size(28f, 18f);
                        } else {
                            title.add("[[JS]").color(Color.valueOf("f1e05a")).style(Styles.outlineLabel);
                        }
                    }
                }).row();
                
                info.add("[lightgray]by " + mod.author + " [gray]| v" + mod.version).padTop(4f).row();
                
                if (!mod.description.isEmpty()) {
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
                }).tooltip("Info");
                
                btns.button(Icon.link, Styles.clearNonei, () -> {
                    Core.app.openURI(mod.repo);
                }).tooltip("GitHub");
                
                if (installed == null) {
                    btns.button(Icon.download, Styles.clearNonei, () -> {
                        installMod(mod);
                    }).tooltip("Install");
                } else {
                    btns.image(Icon.ok).size(40f).color(Color.green);
                }
            }).right().padRight(10f);
            
        }).fillX().height(120f).pad(4f).row();
    }
    
    void installMod(ModInfo mod) {
        try {
            String repo = mod.repo.replace("https://github.com/", "");
            Vars.ui.mods.githubImportMod(repo, true);
            Vars.ui.showInfo("Installing " + mod.name + "...");
        } catch (Exception e) {
            Log.err("Install error", e);
            Vars.ui.showErrorMessage("Install failed: " + e.getMessage());
        }
    }void showModDetails(ModInfo mod) {
        Mods.LoadedMod installed = Vars.mods.list().find(m -> m.name.equals(mod.name) || m.meta.name.equals(mod.name));
        
        BaseDialog dialog = new BaseDialog(mod.name);
        dialog.addCloseButton();
        
        Table content = new Table(Tex.pane);
        content.margin(15f);
        
        if (installed != null && installed.iconTexture != null) {
            content.image(new TextureRegion(installed.iconTexture)).size(80f).pad(10f).row();
        } else {
            content.image(Icon.box).size(80f).pad(10f).row();
        }
        
        Table titleRow = new Table();
        titleRow.add("[accent]" + mod.name).pad(5f);
        if (mod.isJava) {
            if (javaBadge != null && javaBadge.found()) {
                titleRow.image(javaBadge).size(36f, 22f).padLeft(8f);
            } else {
                titleRow.add(" [[JAVA]").color(Color.valueOf("b07219"));
            }
        } else {
            if (jsBadge != null && jsBadge.found()) {
                titleRow.image(jsBadge).size(36f, 22f).padLeft(8f);
            } else {
                titleRow.add(" [[JS]").color(Color.valueOf("f1e05a"));
            }
        }
        content.add(titleRow).row();
        
        content.add("[cyan]" + mod.author).pad(5f).row();
        content.add("[lightgray]v" + mod.version).pad(3f).row();
        
        if (!mod.description.isEmpty()) {
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
                Core.app.openURI(mod.repo);
            }).size(220f, 55f).pad(5f);
            
            if (installed == null) {
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
    }
    
    void loadGitHubStats(ModInfo mod, Table statsTable) {
        String key = mod.repo;
        if (statsCache.containsKey(key)) {
            displayStats(statsTable, mod, statsCache.get(key));
            return;
        }
        
        fetchModStats(mod, stats -> {
            if (stats != null) {
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
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) {
                    callback.get(null);
                    return;
                }
                
                String owner = parts[0];
                String repo = parts[1];
                
                repoConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo).openConnection();
                repoConn.setRequestProperty("User-Agent", "Mindustry");
                repoConn.setConnectTimeout(10000);
                repoConn.setReadTimeout(10000);
                
                if (repoConn.getResponseCode() != 200) {
                    callback.get(null);
                    return;
                }
                
                BufferedReader repoReader = new BufferedReader(new InputStreamReader(repoConn.getInputStream()));
                StringBuilder repoData = new StringBuilder();
                String line;
                while ((line = repoReader.readLine()) != null) repoData.append(line);
                repoReader.close();
                
                ModStats stats = new ModStats();
                
                try {
                    JsonValue repoJson = new JsonReader().parse(repoData.toString());
                    stats.stars = repoJson.getInt("stargazers_count", 0);
                } catch (Exception e) {
                    Log.err("Parse repo", e);
                }
                
                try {
                    relConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases").openConnection();
                    relConn.setRequestProperty("User-Agent", "Mindustry");
                    relConn.setConnectTimeout(10000);
                    relConn.setReadTimeout(10000);
                    
                    if (relConn.getResponseCode() == 200) {
                        BufferedReader relReader = new BufferedReader(new InputStreamReader(relConn.getInputStream()));
                        StringBuilder relData = new StringBuilder();
                        while ((line = relReader.readLine()) != null) relData.append(line);
                        relReader.close();
                        
                        JsonValue releasesJson = new JsonReader().parse(relData.toString());
                        stats.releases = releasesJson.size;
                        
                        int totalDownloads = 0;
                        for (JsonValue release : releasesJson) {
                            JsonValue assets = release.get("assets");
                            if (assets != null) {
                                for (JsonValue asset : assets) {
                                    totalDownloads += asset.getInt("download_count", 0);
                                }
                            }
                        }
                        stats.downloads = totalDownloads;
                    }
                } catch (Exception e) {
                    Log.err("Parse releases", e);
                }
                
                callback.get(stats);
                
            } catch (Exception e) {
                Log.err("GitHub API", e);
                callback.get(null);
            } finally {
                if (repoConn != null) try { repoConn.disconnect(); } catch (Exception e) {}
                if (relConn != null) try { relConn.disconnect(); } catch (Exception e) {}
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
        long lastUpdatedTime = 0;
        boolean isJava = false;
    }
    
    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}