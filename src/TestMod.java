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
    private int modsPerPage = 10;
    private String searchQuery = "";
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private Color accentColor = Color.valueOf("ffd37f");
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;

    public TestMod() {
        Log.info("ModInfo+ Enhanced: Initializing");
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
        if (!javaBadge.found()) javaBadge = Core.atlas.find("error");
        if (!jsBadge.found()) jsBadge = Core.atlas.find("error");
        Log.info("Badges loaded");
    }
    
    void addModInfoButton() {
        BaseDialog mods = Vars.ui.mods;
        TextButton btn = new TextButton("ModInfo+");
        btn.clicked(() -> showEnhancedBrowser());
        mods.buttons.add(btn).size(200f, 64f);
    }
    
    void showEnhancedBrowser() {
        if (browserDialog != null) {
            browserDialog.show();
            return;
        }
        browserDialog = new BaseDialog("ModInfo+");
        browserDialog.addCloseButton();
        Table main = new Table();
        main.background(Tex.pane);
        main.table(searchBar -> {
            searchBar.image(Icon.zoom).size(20f).pad(5f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.changed(() -> updateSearchQuery(searchField.getText()));
            searchBar.add(searchField).growX().pad(5f);
            searchBar.button(Icon.cancel, Styles.clearNonei, () -> {
                searchField.setText("");
                updateSearchQuery("");
            }).size(40f);
        }).fillX().pad(10f).row();
        statusLabel = new Label("");
        main.add(statusLabel).pad(5f).row();
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        main.add(pane).grow().pad(5f).row();
        paginationBar = new Table();
        buildPaginationBar();
        main.add(paginationBar).fillX().pad(5f).row();
        main.button("Load Mods", Icon.download, () -> fetchModList()).size(200f, 50f).pad(8f);
        browserDialog.cont.add(main).grow();
        browserDialog.show();
        updateStatusLabel("Click 'Load Mods'");
    }
    
    void buildPaginationBar() {
        paginationBar.clearChildren();
        paginationBar.button("◄", () -> {
            if (currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).size(50f).disabled(b -> currentPage == 0);
        paginationBar.add().growX();
        paginationBar.label(() -> (currentPage + 1) + " / " + (getMaxPage() + 1));
        paginationBar.add().growX();
        paginationBar.button("►", () -> {
            if (currentPage < getMaxPage()) {
                currentPage++;
                updateVisibleMods();
            }
        }).size(50f).disabled(b -> currentPage >= getMaxPage());
    }
    
    void updateSearchQuery(String query) {
        searchQuery = query.toLowerCase();
        currentPage = 0;
        applyFilter();
        updateVisibleMods();
    }
    
    void applyFilter() {
        filteredMods.clear();
        if (searchQuery.isEmpty()) {
            filteredMods.addAll(allMods);
        } else {
            for (ModInfo mod : allMods) {
                if (mod.name.toLowerCase().contains(searchQuery) || 
                    mod.author.toLowerCase().contains(searchQuery)) {
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
            modListContainer.add("[scarlet]No mods found").pad(20f);
        } else {
            for (int i = start; i < end; i++) {
                buildModRow(modListContainer, filteredMods.get(i));
            }
        }
        updateStatusLabel(filteredMods.size + " mods");
    }
    
    void updateStatusLabel(String text) {
        statusLabel.setText(text);
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }
    
    void fetchModList() {
        updateStatusLabel("[cyan]Loading...");
        Core.app.post(() -> {
            try {
                String url = "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
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
                Core.app.post(() -> updateStatusLabel("[scarlet]Failed to load"));
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
                mod.version = modJson.getString("version", "");
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
        table.table(Tex.button, card -> {
            card.margin(8f);
            card.left();
            
            card.table(left -> {
                left.left();
                left.image(Icon.box).size(48f).pad(4f);
                
                left.table(info -> {
                    info.left();
                    info.defaults().left();
                    
                    info.table(titleRow -> {
                        titleRow.left();
                        Label nameLabel = new Label(mod.name);
                        nameLabel.setColor(accentColor);
                        titleRow.add(nameLabel).padRight(8f);
                        
                        if (mod.isJava && javaBadge.found()) {
                            titleRow.image(javaBadge).size(24f, 16f).padLeft(4f);
                        } else if (!mod.isJava && jsBadge.found()) {
                            titleRow.image(jsBadge).size(24f, 16f).padLeft(4f);
                        }
                    }).row();
                    
                    info.add("[lightgray]" + mod.author).padTop(2f).row();
                    info.add("[darkgray]" + formatDate(mod.lastUpdated)).padTop(2f);
                }).growX().pad(8f);
            }).growX();
            
            card.table(actions -> {
                actions.defaults().size(40f);
                actions.button(Icon.info, Styles.clearNonei, () -> {
                    showModDetails(mod);
                });
                actions.button(Icon.link, Styles.clearNonei, () -> {
                    Core.app.openURI(mod.repo);
                });
            }).right().padRight(8f);
            
        }).fillX().pad(2f).row();
    }
    
    void showModDetails(ModInfo mod) {
        BaseDialog dialog = new BaseDialog(mod.name);
        dialog.addCloseButton();
        Table content = new Table();
        content.background(Tex.button);
        content.margin(15f);
        
        content.image(Icon.box).size(64f).pad(8f).row();
        
        Table titleRow = new Table();
        titleRow.add("[accent]" + mod.name).padRight(10f);
        if (mod.isJava && javaBadge.found()) {
            titleRow.image(javaBadge).size(32f, 20f);
        } else if (!mod.isJava && jsBadge.found()) {
            titleRow.image(jsBadge).size(32f, 20f);
        }
        content.add(titleRow).row();
        content.add("[cyan]" + mod.author).pad(4f).row();
        
        if (mod.description != null && !mod.description.isEmpty()) {
            Label desc = new Label(mod.description);
            desc.setWrap(true);
            desc.setAlignment(Align.center);
            content.add(desc).width(400f).pad(8f).row();
        }
        
        content.image().height(2f).width(300f).color(accentColor).pad(8f).row();
        
        Table statsTable = new Table();
        statsTable.add("[cyan]⟳ Loading stats...").pad(10f);
        content.add(statsTable).row();
        
        content.image().height(2f).width(300f).color(accentColor).pad(8f).row();
        
        content.button("Open on GitHub", Icon.link, () -> {
            Core.app.openURI(mod.repo);
        }).size(200f, 48f).pad(8f);
        
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
            statsTable.defaults().left().pad(4f);
            statsTable.add("[yellow]★ Stars:").padRight(10f);
            statsTable.add("[accent]" + stats.stars).row();
            statsTable.add("[lime]↓ Downloads:").padRight(10f);
            statsTable.add("[lime]" + stats.downloads).row();
            statsTable.add("[cyan]⚡ Releases:").padRight(10f);
            statsTable.add("[cyan]" + stats.releases).row();
            statsTable.add("[lightgray]📦 Version:").padRight(10f);
            statsTable.add("[lightgray]" + mod.version).row();
            statsTable.add("[darkgray]🕐 Updated:").padRight(10f);
            statsTable.add("[darkgray]" + formatDate(mod.lastUpdated)).row();
        });
    }
    
    void displayStatsError(Table statsTable, ModInfo mod) {
        Core.app.post(() -> {
            statsTable.clearChildren();
            statsTable.defaults().left().pad(4f);
            statsTable.add("[scarlet]✗ Could not load stats").colspan(2).row();
            statsTable.add("[lightgray]📦 Version:").padRight(10f);
            statsTable.add("[lightgray]" + mod.version).row();
            statsTable.add("[darkgray]🕐 Updated:").padRight(10f);
            statsTable.add("[darkgray]" + formatDate(mod.lastUpdated)).row();
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
                repoConn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                repoConn.setConnectTimeout(8000);
                repoConn.setReadTimeout(8000);
                if (repoConn.getResponseCode() != 200) {
                    callback.get(null);
                    return;
                }
                BufferedReader repoReader = new BufferedReader(new InputStreamReader(repoConn.getInputStream()));
                StringBuilder repoData = new StringBuilder();
                String line;
                while ((line = repoReader.readLine()) != null) repoData.append(line);
                repoReader.close();
                relConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases").openConnection();
                relConn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                relConn.setConnectTimeout(8000);
                relConn.setReadTimeout(8000);
                ModStats stats = new ModStats();
                if (relConn.getResponseCode() == 200) {
                    BufferedReader relReader = new BufferedReader(new InputStreamReader(relConn.getInputStream()));
                    StringBuilder relData = new StringBuilder();
                    while ((line = relReader.readLine()) != null) relData.append(line);
                    relReader.close();
                    try {
                        JsonValue repoJson = new JsonReader().parse(repoData.toString());
                        stats.stars = repoJson.getInt("stargazers_count", 0);
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
                    } catch (Exception e) {
                        Log.err("Stats parse error", e);
                    }
                } else {
                    try {
                        JsonValue repoJson = new JsonReader().parse(repoData.toString());
                        stats.stars = repoJson.getInt("stargazers_count", 0);
                    } catch (Exception e) {}
                }
                callback.get(stats);
            } catch (Exception e) {
                Log.err("GitHub API error", e);
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