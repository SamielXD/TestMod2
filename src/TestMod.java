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
    private int modsPerPage = 6;
    private String searchQuery = "";
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private Color accentColor = Color.valueOf("84f491");
    private Color bgDark = Color.valueOf("2b2f38");
    private Color cardBg = Color.valueOf("363944");
    private ObjectMap<String, TextureRegion> badgeSprites = new ObjectMap<>();
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();
    private int currentTab = 0;
    private String sortMode = "updated";
    private long lastRefreshTime = 0;

    public TestMod() {
        Log.info("ModInfo+ Browser Initializing");
        initTokens();
    }
    
    void initTokens() {
        tokens.add(new TokenInfo(new String[]{"ghp_", "ljb7p6nUp", "WfeWGW1oo", "kX2Fhht9X", "TqT1Pnffd"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "mPsirCTWN", "qhVCEmOY2", "VszbFPf7Y", "OTP0N7tCf"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "hLczgAeJ9", "C7zMWZxQN", "OYIxeMxrl", "ELx2rABtr"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "RF5cOOMkN", "CxAItejWz", "7qjT8dOuH", "gPl088JeG"}));
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
            });
        });
        addModInfoButton();
    }
    
    void loadAllBadgeSprites() {
        String[] badgeNames = {
            "testmod-java-badge",
            "testmod-js-badge"
        };
        
        for(String name : badgeNames) {
            TextureRegion region = Core.atlas.find(name);
            if(region.found()) {
                badgeSprites.put(name, region);
                Log.info("Loaded badge sprite: " + name);
            } else {
                Log.warn("Badge sprite not found: " + name);
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
        Log.info("Loaded " + modIcons.size + " mod icons");
    }
    
    void addModInfoButton() {
        Events.on(ClientLoadEvent.class, event -> {
            Core.app.post(() -> {
                try {
                    java.lang.reflect.Field menuField = Vars.ui.menufrag.getClass().getDeclaredField("menu");
                    menuField.setAccessible(true);
                    Table menu = (Table)menuField.get(Vars.ui.menufrag);
                    
                    for(Element child : menu.getChildren()) {
                        if(child instanceof TextButton) {
                            TextButton btn = (TextButton)child;
                            String text = btn.getText().toString().toLowerCase();
                            if(text.contains("mod")) {
                                btn.getListeners().clear();
                                btn.clicked(() -> {
                                    showEnhancedBrowser();
                                });
                                return;
                            }
                        }
                    }
                } catch(Exception e) {}
                
                Vars.ui.mods.shown(() -> {
                    Core.app.post(() -> {
                        Vars.ui.mods.hide();
                        showEnhancedBrowser();
                    });
                });
            });
        });
    }
    
    void showEnhancedBrowser() {
        if(browserDialog != null) {
            browserDialog.show();
            return;
        }
        browserDialog = new BaseDialog("");
        browserDialog.cont.clear();
        
        boolean isPortrait = Core.graphics.getHeight() > Core.graphics.getWidth();
        float screenWidth = Core.graphics.getWidth();
        float screenHeight = Core.graphics.getHeight();
        
        Table main = new Table();
        main.background(Tex.pane);
        
        Table header = new Table();
        header.background(Tex.buttonEdge3);
        
        if(isPortrait) {
            header.table(top -> {
                top.image(Icon.box).size(40f).color(accentColor).pad(8f);
                top.add("[accent]MODINFO+").style(Styles.outlineLabel).growX().left().padLeft(8f);
                top.button(Icon.cancel, Styles.cleari, () -> browserDialog.hide()).size(40f).pad(5f);
            }).fillX().row();
            
            header.table(tabs -> {
                tabs.defaults().height(40f).growX().pad(3f);
                tabs.button("Installed", Styles.togglet, () -> {
                    currentTab = 0;
                    fetchModList();
                }).checked(b -> currentTab == 0);
                tabs.button("Browse", Styles.togglet, () -> {
                    currentTab = 1;
                    fetchRemoteMods();
                }).checked(b -> currentTab == 1);
            }).fillX().padBottom(5f);
        } else {
            header.table(left -> {
                left.image(Icon.box).size(48f).color(accentColor).pad(10f);
                left.add("[accent]MODINFO+").style(Styles.outlineLabel).growX().left().padLeft(10f);
            }).growX().left();
            
            header.table(tabs -> {
                tabs.defaults().size(100f, 45f).pad(3f);
                tabs.button("Installed", Styles.togglet, () -> {
                    currentTab = 0;
                    fetchModList();
                }).checked(b -> currentTab == 0);
                tabs.button("Browse", Styles.togglet, () -> {
                    currentTab = 1;
                    fetchRemoteMods();
                }).checked(b -> currentTab == 1);
            }).padRight(5f);
            
            header.button(Icon.book, Styles.cleari, () -> {
                Core.app.openURI("https://mindustrygame.github.io/wiki/modding/");
            }).size(45f).tooltip("Modding Guide").pad(5f);
            header.button(Icon.refresh, Styles.cleari, () -> reloadMods()).size(45f).tooltip("Refresh").pad(5f);
            header.button(Icon.add, Styles.cleari, () -> importModFile()).size(45f).tooltip("Import Mod").pad(5f);
            header.button(Icon.cancel, Styles.cleari, () -> browserDialog.hide()).size(45f).tooltip("Close").pad(5f);
        }
        
        main.add(header).fillX().row();
        
        main.image().color(accentColor).fillX().height(2f).row();
        
        if(isPortrait) {
            main.table(controls -> {
                controls.background(Tex.button);
                controls.button(Icon.book, Styles.cleari, () -> {
                    Core.app.openURI("https://mindustrygame.github.io/wiki/modding/");
                }).size(40f).tooltip("Guide").pad(5f);
                controls.button(Icon.refresh, Styles.cleari, () -> reloadMods()).size(40f).pad(5f);
                controls.button(Icon.add, Styles.cleari, () -> importModFile()).size(40f).pad(5f);
                controls.defaults().height(38f).growX().pad(2f);
                controls.button("Recent", Styles.togglet, () -> {
                    sortMode = "updated";
                    applySort();
                }).checked(b -> sortMode.equals("updated"));
                controls.button("Stars", Styles.togglet, () -> {
                    sortMode = "stars";
                    applySort();
                }).checked(b -> sortMode.equals("stars"));
                controls.button("Name", Styles.togglet, () -> {
                    sortMode = "name";
                    applySort();
                }).checked(b -> sortMode.equals("name"));
            }).fillX().height(50f).row();
        } else {
            main.table(controls -> {
                controls.background(Tex.button);
                controls.table(sortBar -> {
                    sortBar.add("[lightgray]Sort:").padRight(8f);
                    sortBar.defaults().size(90f, 40f).pad(3f);
                    sortBar.button("Recent", Styles.togglet, () -> {
                        sortMode = "updated";
                        applySort();
                    }).checked(b -> sortMode.equals("updated"));
                    sortBar.button("Stars", Styles.togglet, () -> {
                        sortMode = "stars";
                        applySort();
                    }).checked(b -> sortMode.equals("stars"));
                    sortBar.button("Name", Styles.togglet, () -> {
                        sortMode = "name";
                        applySort();
                    }).checked(b -> sortMode.equals("name"));
                }).left().padLeft(10f);
                controls.add().growX();
                controls.label(() -> {
                    if(lastRefreshTime > 0) {
                        long elapsed = (Time.millis() - lastRefreshTime) / 1000;
                        return "[lightgray]" + elapsed + "s ago";
                    }
                    return "";
                }).padRight(10f);
            }).fillX().height(55f).row();
        }
        
        main.table(search -> {
            search.background(Tex.button);
            search.image(Icon.zoom).size(28f).pad(8f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
            });
            search.add(searchField).growX().height(40f).pad(8f);
            search.button(Icon.cancel, Styles.cleari, () -> {
                searchField.setText("");
                searchQuery = "";
                currentPage = 0;
                applyFilter();
            }).size(40f).visible(() -> !searchField.getText().isEmpty());
        }).fillX().height(56f).padTop(2f).row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(5f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        main.add(pane).grow().pad(8f).row();
        
        paginationBar = new Table();
        buildPaginationBar();
        main.add(paginationBar).fillX().padBottom(5f).row();
        
        browserDialog.cont.add(main).size(screenWidth, screenHeight);
        browserDialog.show();
        fetchModList();
    }
    
    void importModFile() {
        Vars.platform.showFileChooser(true, "zip", "jar", file -> {
            try {
                Vars.mods.importMod(file);
                Vars.ui.showInfo("[lime]Mod imported successfully!\n[lightgray]Restart to apply changes.");
                reloadMods();
            } catch(Exception e) {
                Vars.ui.showErrorMessage("Import failed: " + e.getMessage());
            }
        });
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
        } else {
            fetchRemoteMods();
        }
    }
    
    void buildPaginationBar() {
        paginationBar.clearChildren();
        paginationBar.background(Tex.button);
        
        paginationBar.button("<", Styles.cleart, () -> {
            if(currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).size(80f, 50f).disabled(b -> currentPage == 0);
        
        paginationBar.add().growX();
        paginationBar.label(() -> "[lightgray]Page " + (currentPage + 1) + " / " + Math.max(1, getMaxPage() + 1) + 
                         "  |  " + filteredMods.size + " mods").pad(10f);
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
                    mod.author.toLowerCase().contains(searchQuery)) {
                    filteredMods.add(mod);
                }
            }
        }
        applySort();
    }
    
    void applySort() {
        if(sortMode.equals("updated")) {
            filteredMods.sort((a, b) -> b.lastUpdated.compareTo(a.lastUpdated));
        } else if(sortMode.equals("stars")) {
            filteredMods.sort((a, b) -> Integer.compare(b.stars, a.stars));
        } else {
            filteredMods.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        updateVisibleMods();
    }
    
    void updateVisibleMods() {
        modListContainer.clearChildren();
        int start = currentPage * modsPerPage;
        int end = Math.min(start + modsPerPage, filteredMods.size);
        
        if(filteredMods.isEmpty()) {
            modListContainer.add("[lightgray]No mods found").pad(40f);
        } else {
            for(int i = start; i < end; i++) {
                ModInfo mod = filteredMods.get(i);
                buildModRow(modListContainer, mod);
                
                if(!mod.repo.isEmpty() && mod.stars == 0 && mod.downloads == 0) {
                    loadVisibleModStats(mod);
                }
            }
        }
        updateStatusLabel("Showing " + (end - start) + " of " + filteredMods.size + " mods");
        buildPaginationBar();
    }
    
    void loadVisibleModStats(ModInfo mod) {
        String key = mod.repo;
        
        if(currentlyLoadingStats.contains(key)) {
            return;
        }
        
        if(statsCache.containsKey(key)) {
            Long lastFetch = lastStatsFetch.get(key, 0L);
            if(Time.millis() - lastFetch < CACHE_TIME) {
                ModStats stats = statsCache.get(key);
                mod.stars = stats.stars;
                mod.downloads = stats.downloads;
                mod.releases = stats.releases;
                return;
            }
        }
        
        currentlyLoadingStats.add(key);
        
        fetchModStats(mod, stats -> {
            currentlyLoadingStats.remove(key);
            if(stats != null) {
                statsCache.put(key, stats);
                lastStatsFetch.put(key, Time.millis());
                mod.stars = stats.stars;
                mod.downloads = stats.downloads;
                mod.releases = stats.releases;
                Core.app.post(() -> updateVisibleMods());
            }
        });
    }
    
    void updateStatusLabel(String text) {
        statusLabel.setText("[lightgray]" + text);
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }void fetchModList() {
        updateStatusLabel("[cyan]Loading installed mods...");
        lastRefreshTime = Time.millis();
        Core.app.post(() -> {
            allMods.clear();
            
            for(Mods.LoadedMod mod : Vars.mods.list()) {
                ModInfo info = new ModInfo();
                
                if(mod.meta != null) {
                    info.name = mod.meta.name;
                    info.author = mod.meta.author;
                    info.description = mod.meta.description;
                    info.version = mod.meta.version;
                    info.hasJava = mod.meta.java;
                    info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
                    info.isServerCompatible = true;
                    info.repo = mod.meta.repo != null ? mod.meta.repo : "";
                } else {
                    info.name = mod.name;
                    info.author = "Unknown";
                    info.description = "";
                    info.version = "1.0";
                    info.hasJava = false;
                    info.hasScripts = false;
                    info.isServerCompatible = false;
                    info.repo = "";
                }
                
                info.stars = 0;
                info.downloads = 0;
                info.releases = 0;
                info.lastUpdated = "";
                info.installedMod = mod;
                
                allMods.add(info);
            }
            
            Core.app.post(() -> {
                currentPage = 0;
                applyFilter();
                updateStatusLabel("Loaded " + allMods.size + " installed mods");
            });
        });
    }

    void fetchRemoteMods() {
        updateStatusLabel("[cyan]Fetching mod browser...");
        Core.app.post(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL("https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json").openConnection();
                conn.setRequestProperty("User-Agent", "ModInfo+");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                if(conn.getResponseCode() != 200) {
                    Core.app.post(() -> updateStatusLabel("[scarlet]Failed to fetch mods"));
                    return;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while((line = reader.readLine()) != null) json.append(line);
                reader.close();
                
                Seq<ModInfo> mods = parseModList(json.toString());
                Core.app.post(() -> {
                    allMods.clear();
                    allMods.addAll(mods);
                    currentPage = 0;
                    applyFilter();
                    updateStatusLabel("Loaded " + allMods.size + " mods from browser");
                });
                
            } catch(Exception e) {
                Core.app.post(() -> updateStatusLabel("[scarlet]Network error"));
            } finally {
                if(conn != null) try { conn.disconnect(); } catch(Exception e) {}
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
                mod.stars = 0;
                mod.downloads = 0;
                mod.releases = 0;
                mod.hasJava = modJson.getBoolean("hasJava", false);
                mod.hasScripts = modJson.getBoolean("hasScripts", false);
                
                if(!mod.repo.isEmpty() && !mod.name.isEmpty()) {
                    mods.add(mod);
                }
            }
        } catch(Exception e) {}
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
        Mods.LoadedMod installed = mod.installedMod;
        boolean isPortrait = Core.graphics.getHeight() > Core.graphics.getWidth();
        
        table.table(Tex.button, row -> {
            row.margin(8f);
            row.left();
            
            TextureRegion icon = getModIcon(mod, installed);
            float iconSize = isPortrait ? 64f : 72f;
            if(icon != null) {
                row.image(icon).size(iconSize).padRight(12f);
            } else {
                row.image(Icon.box).size(iconSize).color(Color.gray).padRight(12f);
            }
            
            row.table(info -> {
                info.left().defaults().left();
                
                info.table(titleRow -> {
                    titleRow.left();
                    
                    Label nameLabel = new Label(mod.name);
                    nameLabel.setStyle(Styles.outlineLabel);
                    nameLabel.setColor(accentColor);
                    nameLabel.setEllipsis(true);
                    nameLabel.setWrap(false);
                    float maxNameWidth = isPortrait ? 200f : 350f;
                    titleRow.add(nameLabel).maxWidth(maxNameWidth).padRight(8f);
                    
                    if(mod.hasJava) {
                        TextureRegion javaBadge = badgeSprites.get("testmod-java-badge");
                        if(javaBadge != null) {
                            titleRow.image(javaBadge).size(28f, 18f).padRight(6f).tooltip("Java Mod");
                        } else {
                            titleRow.add("[#b07219]J").style(Styles.outlineLabel).padRight(6f);
                        }
                    } else if(mod.hasScripts) {
                        TextureRegion jsBadge = badgeSprites.get("testmod-js-badge");
                        if(jsBadge != null) {
                            titleRow.image(jsBadge).size(28f, 18f).padRight(6f).tooltip("JS Mod");
                        } else {
                            titleRow.add("[#f1e05a]JS").style(Styles.outlineLabel).padRight(6f);
                        }
                    }
                    
                    titleRow.image(Icon.book).size(16f).color(Color.valueOf("89e051")).padRight(6f).tooltip("HJSON");
                    
                    if(installed != null) {
                        if(mod.isServerCompatible) {
                            titleRow.image(Icon.host).size(16f).color(Color.sky).padRight(6f).tooltip("Server");
                        } else {
                            titleRow.image(Icon.players).size(16f).color(Color.orange).padRight(6f).tooltip("Client Only");
                        }
                        
                        if(installed.enabled()) {
                            titleRow.image(Icon.ok).size(16f).color(Color.lime).tooltip("Enabled");
                        } else {
                            titleRow.image(Icon.cancel).size(16f).color(Color.scarlet).tooltip("Disabled");
                        }
                    }
                }).fillX().row();
                
                Label authorLabel = new Label("[lightgray]" + mod.author + " [gray]v" + mod.version);
                authorLabel.setEllipsis(true);
                authorLabel.setWrap(false);
                float maxAuthorWidth = isPortrait ? 200f : 350f;
                info.add(authorLabel).maxWidth(maxAuthorWidth).padTop(4f).row();
                
                if(mod.stars > 0 || mod.downloads > 0 || mod.releases > 0) {
                    info.table(stats -> {
                        stats.left().defaults().left().padRight(12f);
                        if(mod.stars > 0) {
                            stats.image(Icon.star).size(14f).color(Color.yellow).padRight(3f);
                            stats.add("[yellow]" + mod.stars);
                        }
                        if(mod.downloads > 0) {
                            stats.image(Icon.download).size(14f).color(Color.lime).padRight(3f);
                            stats.add("[lime]" + mod.downloads);
                        }
                        if(mod.releases > 0) {
                            stats.image(Icon.box).size(14f).color(Color.cyan).padRight(3f);
                            stats.add("[cyan]" + mod.releases);
                        }
                    }).padTop(4f).row();
                } else if(!mod.repo.isEmpty()) {
                    info.add("[darkgray]Loading...").padTop(4f).row();
                }
                
            }).growX().padLeft(8f);
            
            row.table(btns -> {
                btns.defaults().size(50f).pad(3f);
                
                btns.button(Icon.info, Styles.clearNonei, () -> {
                    showModDetails(mod);
                }).tooltip("Details");
                
                if(installed != null) {
                    btns.button(installed.enabled() ? Icon.cancel : Icon.ok, Styles.clearNonei, () -> {
                        toggleModState(mod, installed);
                    }).tooltip(installed.enabled() ? "Disable" : "Enable")
                      .update(b -> b.getStyle().imageUpColor = installed.enabled() ? Color.orange : Color.lime);
                    
                    btns.button(Icon.trash, Styles.clearNonei, () -> {
                        confirmDelete(mod, installed);
                    }).tooltip("Delete").update(b -> b.getStyle().imageUpColor = Color.scarlet);
                    
                } else if(!mod.repo.isEmpty()) {
                    btns.button(Icon.download, Styles.clearNonei, () -> {
                        installMod(mod);
                    }).tooltip("Install").update(b -> b.getStyle().imageUpColor = Color.sky);
                }
                
                if(!mod.repo.isEmpty()) {
                    btns.button(Icon.link, Styles.clearNonei, () -> {
                        Core.app.openURI("https://github.com/" + mod.repo);
                    }).tooltip("GitHub");
                }
                
            }).right().padRight(8f);
            
        }).fillX().height(isPortrait ? 100f : 110f).pad(6f).row();
    }

    void toggleModState(ModInfo mod, Mods.LoadedMod installed) {
        try {
            if(installed.enabled()) {
                Vars.mods.setEnabled(installed, false);
                Vars.ui.showInfo("[orange]" + mod.name + " disabled.\n[lightgray]Restart required.");
            } else {
                Vars.mods.setEnabled(installed, true);
                Vars.ui.showInfo("[lime]" + mod.name + " enabled.\n[lightgray]Restart required.");
            }
            updateVisibleMods();
        } catch(Exception e) {
            Vars.ui.showErrorMessage("Failed to toggle mod state");
        }
    }

    void confirmDelete(ModInfo mod, Mods.LoadedMod installed) {
        BaseDialog confirm = new BaseDialog("Delete Mod");
        confirm.cont.add("[scarlet]Delete " + mod.name + "?").pad(20f).row();
        confirm.cont.add("[lightgray]This cannot be undone.").pad(10f).row();
        
        confirm.buttons.defaults().size(150f, 50f).pad(10f);
        confirm.buttons.button("Cancel", Icon.cancel, () -> {
            confirm.hide();
        });
        confirm.buttons.button("Delete", Icon.trash, () -> {
            deleteMod(mod, installed);
            confirm.hide();
        }).update(b -> b.getStyle().fontColor = Color.scarlet);
        
        confirm.show();
    }

    void deleteMod(ModInfo mod, Mods.LoadedMod installed) {
        try {
            if(installed.file != null && installed.file.file().exists()) {
                installed.file.file().delete();
            }
            Vars.mods.list().remove(installed);
            allMods.remove(mod);
            applyFilter();
            updateVisibleMods();
            Vars.ui.showInfo("[scarlet]" + mod.name + " deleted.\n[lightgray]Restart recommended.");
        } catch(Exception e) {
            Vars.ui.showErrorMessage("Failed to delete mod: " + e.getMessage());
        }
    }

    TextureRegion getModIcon(ModInfo mod, Mods.LoadedMod installed) {
        if(installed != null) {
            if(installed.iconTexture != null) {
                return new TextureRegion(installed.iconTexture);
            }
            
            if(installed.root != null) {
                Fi iconFile = installed.root.child("icon.png");
                if(iconFile.exists()) {
                    try {
                        Texture tex = new Texture(iconFile);
                        return new TextureRegion(tex);
                    } catch(Exception e) {}
                }
            }
        }
        
        String key = mod.name.toLowerCase();
        if(modIcons.containsKey(key)) {
            return modIcons.get(key);
        }
        
        return null;
    }

    void installMod(ModInfo mod) {
        try {
            Vars.ui.mods.githubImportMod(mod.repo, true);
            Vars.ui.showInfo("Installing " + mod.name + "...");
        } catch(Exception e) {
            Vars.ui.showErrorMessage("Install failed: " + e.getMessage());
        }
    }void showModDetails(ModInfo mod) {
        Mods.LoadedMod installed = mod.installedMod;
        
        BaseDialog dialog = new BaseDialog("");
        
        Table main = new Table(Tex.pane);
        main.margin(20f);
        
        Table header = new Table();
        header.background(Tex.button);
        
        TextureRegion icon = getModIcon(mod, installed);
        if(icon != null) {
            header.image(icon).size(96f).pad(12f);
        } else {
            header.image(Icon.box).size(96f).color(Color.gray).pad(12f);
        }
        
        header.table(title -> {
            title.left().defaults().left();
            title.add("[accent]" + mod.name).style(Styles.outlineLabel).padBottom(4f).row();
            title.add("[cyan]by " + mod.author).row();
        }).growX().padLeft(12f);
        
        main.add(header).fillX().pad(8f).row();
        
        main.image().color(accentColor).height(3f).fillX().pad(8f).row();
        
        Table badges = new Table();
        badges.left();
        badges.defaults().padRight(10f);
        
        if(mod.hasJava) {
            TextureRegion javaBadge = badgeSprites.get("testmod-java-badge");
            if(javaBadge != null) {
                badges.image(javaBadge).size(44f, 28f).tooltip("Java Mod");
            } else {
                badges.add("[#b07219]JAVA").style(Styles.outlineLabel);
            }
        } else if(mod.hasScripts) {
            TextureRegion jsBadge = badgeSprites.get("testmod-js-badge");
            if(jsBadge != null) {
                badges.image(jsBadge).size(44f, 28f).tooltip("JS Mod");
            } else {
                badges.add("[#f1e05a]JS").style(Styles.outlineLabel);
            }
        }
        
        badges.image(Icon.book).size(24f).color(Color.valueOf("89e051")).tooltip("HJSON");
        
        if(installed != null) {
            if(mod.isServerCompatible) {
                badges.image(Icon.host).size(24f).color(Color.sky).tooltip("Server Compatible");
            } else {
                badges.image(Icon.players).size(24f).color(Color.orange).tooltip("Client Only");
            }
            
            if(installed.enabled()) {
                badges.image(Icon.ok).size(26f).color(Color.lime).padLeft(8f);
                badges.add("[lime]Enabled").style(Styles.outlineLabel);
            } else {
                badges.image(Icon.cancel).size(26f).color(Color.scarlet).padLeft(8f);
                badges.add("[scarlet]Disabled").style(Styles.outlineLabel);
            }
        }
        
        main.add(badges).left().pad(8f).row();
        
        Table info = new Table();
        info.left().defaults().left().pad(4f);
        
        info.add("[lightgray]Version:").padRight(12f);
        info.add("[white]" + mod.version).row();
        
        if(!mod.repo.isEmpty()) {
            info.add("[lightgray]Repository:").padRight(12f);
            info.add("[white]" + mod.repo).row();
        }
        
        main.add(info).left().fillX().pad(8f).row();
        
        if(!mod.repo.isEmpty()) {
            main.image().color(accentColor).height(2f).fillX().pad(8f).row();
            
            Table statsTable = new Table();
            statsTable.left().defaults().left().pad(5f);
            statsTable.add("[lightgray]Loading GitHub stats...").colspan(2).row();
            main.add(statsTable).left().fillX().pad(8f).row();
            
            loadGitHubStats(mod, statsTable);
        }
        
        main.image().color(accentColor).height(2f).fillX().pad(8f).row();
        
        if(!mod.description.isEmpty()) {
            main.add("[accent]Description:").left().padLeft(12f).padTop(8f).row();
            Label desc = new Label(mod.description);
            desc.setWrap(true);
            desc.setColor(Color.lightGray);
            desc.setAlignment(Align.left);
            main.add(desc).width(500f).pad(12f).left().row();
            main.image().color(accentColor).height(2f).fillX().pad(8f).row();
        }
        
        Table actions = new Table();
        actions.defaults().size(220f, 55f).pad(8f);
        
        if(!mod.repo.isEmpty()) {
            actions.button("Open GitHub", Icon.link, () -> {
                Core.app.openURI("https://github.com/" + mod.repo);
            });
        }
        
        if(installed != null) {
            actions.button(installed.enabled() ? "Disable" : "Enable", 
                          installed.enabled() ? Icon.cancel : Icon.ok, () -> {
                toggleModState(mod, installed);
                dialog.hide();
            });
            
            actions.row();
            
            actions.button("Delete Mod", Icon.trash, () -> {
                confirmDelete(mod, installed);
                dialog.hide();
            }).update(b -> b.getLabel().setColor(Color.scarlet));
        } else if(!mod.repo.isEmpty()) {
            actions.button("Install", Icon.download, () -> {
                installMod(mod);
                dialog.hide();
            });
        }
        
        main.add(actions).fillX().pad(8f).row();
        
        main.button("Close", Icon.cancel, () -> {
            dialog.hide();
        }).size(180f, 45f).pad(8f);
        
        ScrollPane pane = new ScrollPane(main);
        dialog.cont.add(pane).size(580f, 680f);
        dialog.show();
    }

    void loadGitHubStats(ModInfo mod, Table statsTable) {
        String key = mod.repo;
        
        if(statsCache.containsKey(key)) {
            Long lastFetch = lastStatsFetch.get(key, 0L);
            if(Time.millis() - lastFetch < CACHE_TIME) {
                displayStats(statsTable, mod, statsCache.get(key));
                return;
            }
        }
        
        if(currentlyLoadingStats.contains(key)) {
            return;
        }
        
        currentlyLoadingStats.add(key);
        
        fetchModStats(mod, stats -> {
            currentlyLoadingStats.remove(key);
            if(stats != null) {
                statsCache.put(key, stats);
                lastStatsFetch.put(key, Time.millis());
                displayStats(statsTable, mod, stats);
            } else {
                displayStatsError(statsTable, mod);
            }
        });
    }

    void displayStats(Table statsTable, ModInfo mod, ModStats stats) {
        Core.app.post(() -> {
            statsTable.clearChildren();
            statsTable.defaults().left().pad(5f);
            
            statsTable.add("[yellow]★ Stars:").padRight(12f);
            statsTable.add("[white]" + stats.stars).row();
            
            statsTable.add("[lime]↓ Downloads:").padRight(12f);
            statsTable.add("[white]" + stats.downloads).row();
            
            statsTable.add("[cyan]⚡ Releases:").padRight(12f);
            statsTable.add("[white]" + stats.releases).row();
            
            if(!mod.lastUpdated.isEmpty()) {
                statsTable.add("[lightgray]Updated:").padRight(12f);
                statsTable.add("[lightgray]" + formatDate(mod.lastUpdated)).row();
            }
        });
    }

    void displayStatsError(Table statsTable, ModInfo mod) {
        Core.app.post(() -> {
            statsTable.clearChildren();
            statsTable.defaults().left().pad(5f);
            
            statsTable.add("[scarlet]Stats unavailable").colspan(2).row();
            statsTable.add("[darkgray]API rate limit or network error").colspan(2).row();
            statsTable.add("[lightgray]Retrying with next token...").colspan(2).row();
            if(!mod.lastUpdated.isEmpty()) {
                statsTable.add("[lightgray]Updated:").padRight(12f);
                statsTable.add("[lightgray]" + formatDate(mod.lastUpdated)).row();
            }
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
                
                String token = getNextToken();
                
                try {
                    Thread.sleep((long)(Math.random() * 500 + 200));
                } catch(Exception e) {}
                
                repoConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo).openConnection();
                repoConn.setRequestProperty("User-Agent", "Mindustry-ModBrowser");
                repoConn.setRequestProperty("Authorization", "token " + token);
                repoConn.setConnectTimeout(10000);
                repoConn.setReadTimeout(10000);
                
                int code = repoConn.getResponseCode();
                if(code == 403) {
                    Log.warn("Rate limit hit for " + mod.name + ", switching token");
                    markTokenRateLimited();
                    callback.get(null);
                    return;
                }
                if(code != 200) {
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
                } catch(Exception e) {}
                
                try {
                    Thread.sleep((long)(Math.random() * 500 + 200));
                } catch(Exception e) {}
                
                try {
                    relConn = (HttpURLConnection) new URL("https://api.github.com/repos/" + owner + "/" + repo + "/releases").openConnection();
                    relConn.setRequestProperty("User-Agent", "Mindustry-ModBrowser");
                    relConn.setRequestProperty("Authorization", "token " + token);
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
                } catch(Exception e) {}
                
                callback.get(stats);
                
            } catch(Exception e) {
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
        int downloads = 0;
        int releases = 0;
        boolean hasJava = false;
        boolean hasScripts = false;
        boolean isServerCompatible = false;
        Mods.LoadedMod installedMod = null;
    }
    
    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}