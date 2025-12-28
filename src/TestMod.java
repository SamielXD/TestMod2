import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.*;
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
    private ObjectMap<String, TextureRegion> iconCache = new ObjectMap<>();
    private ObjectMap<String, ModStats> statsCache = new ObjectMap<>();
    
    private int currentPage = 0;
    private int modsPerPage = 8;
    private String searchQuery = "";
    
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    
    private Drawable modCardBg;
    private Drawable headerBg;
    private Color accentColor = Color.valueOf("ffd37f");
    
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;

    public TestMod() {
        Log.info("ModInfo+ Enhanced: Initializing");
    }

    @Override
    public void init() {
        loadStyles();
        loadBadges();
        
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                hookModsBrowser();
            });
        });
    }
    
    void loadStyles() {
        modCardBg = Tex.button;
        headerBg = Tex.underline;
    }
    
    void loadBadges() {
        try {
            TextureRegion errorRegion = Core.atlas.find("error");
            
            String[] javaNames = {"testmod-java", "java", "Java"};
            for (String name : javaNames) {
                TextureRegion region = Core.atlas.find(name);
                if (region != errorRegion && region.texture != null) {
                    javaBadge = region;
                    break;
                }
            }
            
            String[] jsNames = {"testmod-js", "js", "Js"};
            for (String name : jsNames) {
                TextureRegion region = Core.atlas.find(name);
                if (region != errorRegion && region.texture != null) {
                    jsBadge = region;
                    break;
                }
            }
        } catch (Exception e) {}
    }
    
    void hookModsBrowser() {
        BaseDialog mods = Vars.ui.mods;

        for (Element el : mods.buttons.getChildren()) {
            if (el instanceof TextButton) {
                TextButton b = (TextButton) el;
                if (b.getText().toString().toLowerCase().contains("browser")) {
                    b.clearListeners();
                    b.clicked(() -> showEnhancedBrowser());
                    Log.info("Mod browser replaced!");
                    return;
                }
            }
        }

        Log.err("Browser button not found");
    }
    
    void showEnhancedBrowser() {
        if (browserDialog != null) {
            browserDialog.show();
            return;
        }

        browserDialog = new BaseDialog("@mods.browser");
        browserDialog.addCloseButton();
        
        Table main = new Table();
        main.background(modCardBg);
        
        main.table(headerBg, header -> {
            header.add("[accent]━━━ [cyan]ModInfo+ Enhanced[] [accent]━━━").pad(8f);
        }).growX().row();
        
        main.table(searchBar -> {
            searchBar.image(Icon.zoom).size(20f).pad(5f);
            
            searchField = new TextField();
            searchField.setMessageText("Search by name or author...");
            searchField.changed(() -> {
                updateSearchQuery(searchField.getText());
            });
            searchBar.add(searchField).growX().pad(5f);
            
            searchBar.button(Icon.cancel, () -> {
                searchField.setText("");
                updateSearchQuery("");
            }).size(40f).pad(3f);
            
        }).growX().pad(5f).row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(5f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        main.add(pane).grow().row();
        
        paginationBar = new Table();
        buildPaginationBar();
        main.add(paginationBar).growX().pad(5f).row();
        
        main.button("Load Mod List", Icon.download, () -> {
            fetchModList();
        }).size(160f, 50f).pad(8f);
        
        browserDialog.cont.add(main).grow();
        
        updateStatusLabel("Click 'Load Mod List' to start");
    }
    
    void buildPaginationBar() {
        paginationBar.clearChildren();
        
        paginationBar.button("◄ Prev", () -> {
            if (currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).width(100f).disabled(b -> currentPage == 0);
        
        paginationBar.add().growX();
        
        paginationBar.button("Next ►", () -> {
            int maxPage = getMaxPage();
            if (currentPage < maxPage) {
                currentPage++;
                updateVisibleMods();
            }
        }).width(100f).disabled(b -> currentPage >= getMaxPage());
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
        
        updateStatusLabel(filteredMods.size + " mods • Page " + (currentPage + 1) + "/" + (getMaxPage() + 1));
        buildPaginationBar();
    }
    
    void updateStatusLabel(String text) {
        statusLabel.setText(text);
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }
    
    TextureRegion getIconFromCache(ModInfo mod) {
        String key = mod.repo;
        
        if (iconCache.containsKey(key)) {
            return iconCache.get(key);
        }
        
        loadIconAsync(mod);
        return null;
    }
    
    void loadIconAsync(ModInfo mod) {
        Core.app.post(() -> {
            try {
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;

                String iconUrl = "https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/master/icon.png";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(iconUrl).openConnection();
                conn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[2048];
                    int n;
                    while ((n = in.read(data)) != -1) buffer.write(data, 0, n);
                    in.close();

                    Core.app.post(() -> {
                        try {
                            Pixmap pixmap = new Pixmap(buffer.toByteArray(), 0, buffer.toByteArray().length);
                            Texture texture = new Texture(pixmap);
                            TextureRegion region = new TextureRegion(texture);
                            
                            iconCache.put(mod.repo, region);
                            updateVisibleMods();
                            pixmap.dispose();
                        } catch (Exception e) {}
                    });
                }
            } catch (Exception e) {}
        });
    }
    
    void fetchModList() {
        updateStatusLabel("[cyan]Loading mod list...");
        
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

                Seq<ModInfo> mods = parseModListProper(response.toString());
                mods.sort(m -> -m.lastUpdatedTime);

                Core.app.post(() -> {
                    allMods = mods;
                    currentPage = 0;
                    applyFilter();
                    updateVisibleMods();
                });
                
            } catch (Exception ex) {
                Core.app.post(() -> updateStatusLabel("[scarlet]Failed to load mods"));
            }
        });
    }
    
    Seq<ModInfo> parseModListProper(String json) {
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
                mod.modType = mod.isJava ? "java" : "javascript";
                
                if (!mod.repo.isEmpty() && !mod.name.isEmpty()) {
                    mods.add(mod);
                }
            }
            
        } catch (Exception e) {}
        
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
    }void buildModRow(Table table, ModInfo mod) {
        table.table(modCardBg, row -> {
            row.left();
            row.margin(6f);
            row.defaults().left();
            
            TextureRegion cachedIcon = getIconFromCache(mod);
            Image iconImg;
            if (cachedIcon != null) {
                iconImg = new Image(new TextureRegionDrawable(cachedIcon));
            } else {
                iconImg = new Image(Icon.box);
            }
            iconImg.setScaling(Scaling.fit);
            row.add(iconImg).size(44f).pad(4f);
            
            row.table(info -> {
                info.left();
                info.defaults().left();
                
                info.table(titleRow -> {
                    titleRow.left();
                    
                    Label nameLabel = new Label("[accent]" + mod.name);
                    nameLabel.setEllipsis(true);
                    titleRow.add(nameLabel).maxWidth(180f).padRight(6f);
                    
                    if (mod.modType != null) {
                        if (mod.modType.equals("java")) {
                            if (javaBadge != null) {
                                Image badge = new Image(new TextureRegionDrawable(javaBadge));
                                badge.setScaling(Scaling.fit);
                                titleRow.add(badge).size(24f, 14f);
                            } else {
                                titleRow.add("[#b07219]JAVA").padLeft(4f);
                            }
                        } else {
                            if (jsBadge != null) {
                                Image badge = new Image(new TextureRegionDrawable(jsBadge));
                                badge.setScaling(Scaling.fit);
                                titleRow.add(badge).size(24f, 14f);
                            } else {
                                titleRow.add("[#f1e05a]JS").padLeft(4f);
                            }
                        }
                    }
                }).growX().row();
                
                Label authorLabel = new Label("[lightgray]" + mod.author);
                authorLabel.setFontScale(0.85f);
                info.add(authorLabel).padTop(2f).row();
                
                Label dateLabel = new Label("[darkgray]" + formatDate(mod.lastUpdated));
                dateLabel.setFontScale(0.8f);
                info.add(dateLabel).padTop(2f);
                
            }).growX().pad(5f);
            
            row.table(actions -> {
                actions.defaults().size(36f).pad(2f);
                
                actions.button(Icon.info, Styles.clearNonei, () -> {
                    showModDetails(mod);
                }).row();
                
                actions.button(Icon.link, Styles.clearNonei, () -> {
                    Core.app.openURI(mod.repo);
                });
                
            }).right().padRight(4f);
            
        }).fillX().height(70f).pad(2f).row();
        
        table.image().height(1f).growX().color(Color.darkGray).pad(1f).row();
    }
    
    void showModDetails(ModInfo mod) {
        BaseDialog dialog = new BaseDialog(mod.name);
        dialog.addCloseButton();
        
        Table content = new Table();
        content.background(modCardBg);
        content.margin(15f);
        content.defaults().center().pad(5f);
        
        buildDetailHeader(content, mod);
        
        Table statsTable = new Table();
        content.add(statsTable).growX().row();
        
        buildDetailActions(content, mod);
        
        ScrollPane pane = new ScrollPane(content);
        dialog.cont.add(pane).grow();
        dialog.show();
        
        loadModStats(mod, statsTable);
    }
    
    void buildDetailHeader(Table content, ModInfo mod) {
        TextureRegion cachedIcon = getIconFromCache(mod);
        if (cachedIcon != null) {
            content.image(new TextureRegionDrawable(cachedIcon)).size(72f).pad(8f).row();
        } else {
            content.image(Icon.box).size(72f).pad(8f).row();
        }
        
        Table titleTable = new Table();
        titleTable.add("[accent]" + mod.name).padRight(10f);
        
        if (mod.modType != null) {
            if (mod.modType.equals("java") && javaBadge != null) {
                titleTable.add(new Image(new TextureRegionDrawable(javaBadge))).size(40f, 24f);
            } else if (mod.modType.equals("javascript") && jsBadge != null) {
                titleTable.add(new Image(new TextureRegionDrawable(jsBadge))).size(40f, 24f);
            } else {
                titleTable.add(mod.modType.equals("java") ? "[#b07219]JAVA MOD" : "[#f1e05a]JS MOD");
            }
        }
        
        content.add(titleTable).row();
        content.add("[cyan]by " + mod.author).pad(4f).row();
        
        if (mod.description != null && !mod.description.isEmpty()) {
            Label desc = new Label(mod.description);
            desc.setWrap(true);
            desc.setAlignment(Align.center);
            content.add(desc).width(400f).pad(8f).row();
        }
        
        content.image().height(2f).growX().color(accentColor).pad(6f).row();
    }
    
    void buildDetailActions(Table content, ModInfo mod) {
        content.image().height(2f).growX().color(accentColor).pad(6f).row();
        
        content.table(actions -> {
            actions.button("Open on GitHub", Icon.link, () -> {
                Core.app.openURI(mod.repo);
            }).size(200f, 48f);
        }).row();
    }
    
    void loadModStats(ModInfo mod, Table statsTable) {
        String key = mod.repo;
        
        if (statsCache.containsKey(key)) {
            displayStats(statsTable, mod, statsCache.get(key));
            return;
        }
        
        statsTable.add("[cyan]⟳ Loading statistics...").row();
        
        fetchModStats(mod, stats -> {
            statsCache.put(key, stats);
            displayStats(statsTable, mod, stats);
        });
    }
    
    void displayStats(Table statsTable, ModInfo mod, ModStats stats) {
        statsTable.clearChildren();
        statsTable.defaults().left().pad(5f);
        
        statsTable.table(grid -> {
            grid.defaults().left().pad(4f);
            
            grid.add("[yellow]★ [white]Stars:").padRight(10f);
            grid.add("[accent]" + stats.stars).row();
            
            grid.add("[lime]↓ [white]Downloads:").padRight(10f);
            grid.add("[lime]" + stats.downloads).row();
            
            grid.add("[cyan]⚡ [white]Releases:").padRight(10f);
            grid.add("[cyan]" + stats.releases).row();
            
            grid.add("[lightgray]📦 [white]Version:").padRight(10f);
            grid.add("[lightgray]" + mod.version).row();
            
            grid.add("[darkgray]🕐 [white]Updated:").padRight(10f);
            grid.add("[darkgray]" + formatDate(mod.lastUpdated)).row();
        });
    }
    
    void fetchModStats(ModInfo mod, Cons<ModStats> callback) {
        Core.app.post(() -> {
            try {
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;

                String owner = parts[0];
                String repo = parts[1];

                HttpURLConnection relConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases").openConnection();
                relConn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                relConn.setConnectTimeout(5000);
                relConn.setReadTimeout(5000);

                BufferedReader relReader = new BufferedReader(new InputStreamReader(relConn.getInputStream()));
                StringBuilder relData = new StringBuilder();
                String line;
                while ((line = relReader.readLine()) != null) relData.append(line);
                relReader.close();

                HttpURLConnection repoConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo).openConnection();
                repoConn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                repoConn.setConnectTimeout(5000);
                repoConn.setReadTimeout(5000);

                BufferedReader repoReader = new BufferedReader(new InputStreamReader(repoConn.getInputStream()));
                StringBuilder repoData = new StringBuilder();
                while ((line = repoReader.readLine()) != null) repoData.append(line);
                repoReader.close();

                ModStats stats = new ModStats();
                
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
                    
                } catch (Exception e) {}

                Core.app.post(() -> callback.get(stats));

            } catch (Exception e) {}
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
        String modType = "javascript";
    }

    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}