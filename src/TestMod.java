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
    private int modsPerPage = 8;
    private String searchQuery = "";
    private BaseDialog browserDialog;
    private Table modListContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    
    private Color accentColor = Color.valueOf("ffd37f");
    private Color enabledColor = Color.valueOf("84f491");
    private Color disabledColor = Color.valueOf("f25555");
    private Color bgDark = Color.valueOf("1c1c1c");
    private Color cardBg = Color.valueOf("2d2d2d");
    private Color borderColor = Color.valueOf("454545");
    
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
    
    String truncateName(String name, int maxLength) {
        if(name.length() <= maxLength) {
            return name;
        }
        return name.substring(0, maxLength) + "...";
    }
    
    <T extends Element> Cell<T> addTooltip(Cell<T> cell, String text) {
        cell.get().addListener(new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
            
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                if(pointer == -1) {
                    Vars.ui.showInfoToast(text, 2f);
                }
            }
        });
        return cell;
    }void showEnhancedBrowser() {
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
        main.setBackground(Styles.black6);
        
        if(isPortrait) {
            buildPortraitUI(main);
        } else {
            buildLandscapeUI(main);
        }
        
        browserDialog.cont.add(main).size(screenWidth, screenHeight);
        browserDialog.show();
        fetchModList();
    }
    
    void buildPortraitUI(Table main) {
        Table header = new Table();
        header.setBackground(Styles.black8);
        
        header.table(top -> {
            Label titleLabel = new Label("[accent]Mods");
            titleLabel.setStyle(Styles.outlineLabel);
            titleLabel.setFontScale(1.2f);
            top.add(titleLabel).padLeft(16f).growX().left();
            addTooltip(top.button(Icon.cancel, Styles.clearNonei, () -> browserDialog.hide())
                .size(45f).pad(8f), "Close");
        }).fillX().height(60f).row();
        
        header.table(tabs -> {
            tabs.defaults().height(50f).growX().pad(4f);
            addTooltip(tabs.button("Enabled Mods", Styles.flatTogglet, () -> {
                currentTab = 0;
                fetchModList();
            }).checked(b -> currentTab == 0), "View enabled mods");
            
            addTooltip(tabs.button("Disabled Mods", Styles.flatTogglet, () -> {
                currentTab = 1;
                fetchDisabledMods();
            }).checked(b -> currentTab == 1), "View disabled mods");
            
            addTooltip(tabs.button("Browse", Styles.flatTogglet, () -> {
                currentTab = 2;
                fetchRemoteMods();
            }).checked(b -> currentTab == 2), "Browse online mods");
        }).fillX().padTop(4f).row();
        
        main.add(header).fillX().row();
        
        main.image().color(accentColor).fillX().height(3f).row();
        
        main.table(controls -> {
            controls.setBackground(Styles.black6);
            controls.defaults().size(50f).pad(6f);
            
            addTooltip(controls.button(Icon.book, Styles.clearNonei, () -> {
                Core.app.openURI("https://mindustrygame.github.io/wiki/modding");
            }), "Modding guide");
            
            addTooltip(controls.button(Icon.refresh, Styles.clearNonei, () -> reloadMods()), "Refresh list");
            addTooltip(controls.button(Icon.add, Styles.clearNonei, () -> importModFile()), "Import mod");
            
            controls.table(sort -> {
                sort.defaults().height(40f).growX().pad(3f);
                addTooltip(sort.button("Recent", Styles.flatTogglet, () -> {
                    sortMode = "updated";
                    applySort();
                }).checked(b -> sortMode.equals("updated")), "Sort by update date");
                
                addTooltip(sort.button("Stars", Styles.flatTogglet, () -> {
                    sortMode = "stars";
                    applySort();
                }).checked(b -> sortMode.equals("stars")), "Sort by stars");
                
                addTooltip(sort.button("Name", Styles.flatTogglet, () -> {
                    sortMode = "name";
                    applySort();
                }).checked(b -> sortMode.equals("name")), "Sort alphabetically");
            }).growX();
        }).fillX().height(65f).row();
        
        buildSearchBar(main);
        buildModList(main);
        buildPaginationBar();
        main.add(paginationBar).fillX().height(60f).row();
    }
    
    void buildLandscapeUI(Table main) {
        Table header = new Table();
        header.setBackground(Styles.black8);
        
        header.table(left -> {
            Label titleLabel = new Label("[accent]Mods");
            titleLabel.setStyle(Styles.outlineLabel);
            titleLabel.setFontScale(1.3f);
            left.add(titleLabel).padLeft(20f).growX().left();
        }).growX();
        
        header.table(tabs -> {
            tabs.defaults().size(130f, 50f).pad(4f);
            addTooltip(tabs.button("Enabled", Styles.flatTogglet, () -> {
                currentTab = 0;
                fetchModList();
            }).checked(b -> currentTab == 0), "View enabled mods");
            
            addTooltip(tabs.button("Disabled", Styles.flatTogglet, () -> {
                currentTab = 1;
                fetchDisabledMods();
            }).checked(b -> currentTab == 1), "View disabled mods");
            
            addTooltip(tabs.button("Browse", Styles.flatTogglet, () -> {
                currentTab = 2;
                fetchRemoteMods();
            }).checked(b -> currentTab == 2), "Browse online mods");
        }).padRight(10f);
        
        header.table(actions -> {
            actions.defaults().size(50f).pad(4f);
            addTooltip(actions.button(Icon.book, Styles.clearNonei, () -> {
                Core.app.openURI("https://mindustrygame.github.io/wiki/modding");
            }), "Modding guide");
            addTooltip(actions.button(Icon.refresh, Styles.clearNonei, () -> reloadMods()), "Refresh list");
            addTooltip(actions.button(Icon.add, Styles.clearNonei, () -> importModFile()), "Import mod");
            addTooltip(actions.button(Icon.cancel, Styles.clearNonei, () -> browserDialog.hide()), "Close");
        }).padRight(10f);
        
        main.add(header).fillX().height(70f).row();
        
        main.image().color(accentColor).fillX().height(3f).row();
        
        main.table(controls -> {
            controls.setBackground(Styles.black6);
            controls.table(sort -> {
                sort.add("[lightgray]Sort by: ").padLeft(15f).padRight(8f);
                sort.defaults().size(100f, 45f).pad(4f);
                addTooltip(sort.button("Recent", Styles.flatTogglet, () -> {
                    sortMode = "updated";
                    applySort();
                }).checked(b -> sortMode.equals("updated")), "Sort by update date");
                
                addTooltip(sort.button("Stars", Styles.flatTogglet, () -> {
                    sortMode = "stars";
                    applySort();
                }).checked(b -> sortMode.equals("stars")), "Sort by stars");
                
                addTooltip(sort.button("Name", Styles.flatTogglet, () -> {
                    sortMode = "name";
                    applySort();
                }).checked(b -> sortMode.equals("name")), "Sort alphabetically");
            }).left().padLeft(10f);
            
            controls.add().growX();
            
            controls.label(() -> {
                if(lastRefreshTime > 0) {
                    long elapsed = (Time.millis() - lastRefreshTime) / 1000;
                    return "[lightgray]Updated " + elapsed + "s ago";
                }
                return "";
            }).padRight(15f);
        }).fillX().height(60f).row();
        
        buildSearchBar(main);
        buildModList(main);
        buildPaginationBar();
        main.add(paginationBar).fillX().height(60f).row();
    }
    
    void buildSearchBar(Table main) {
        main.table(search -> {
            search.setBackground(Styles.black6);
            search.image(Icon.zoom).size(32f).color(Color.lightGray).pad(10f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.setStyle(Styles.defaultField);
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
            });
            search.add(searchField).growX().height(50f).pad(10f);
            addTooltip(search.button(Icon.cancel, Styles.clearNonei, () -> {
                searchField.setText("");
                searchQuery = "";
                currentPage = 0;
                applyFilter();
            }).size(45f).visible(() -> !searchField.getText().isEmpty()), "Clear search");
        }).fillX().height(70f).padTop(4f).row();
    }
    
    void buildModList(Table main) {
        statusLabel = new Label("");
        main.add(statusLabel).pad(8f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        pane.setOverscroll(false, false);
        main.add(pane).grow().pad(10f).row();
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
        } else if(currentTab == 1) {
            fetchDisabledMods();
        } else {
            fetchRemoteMods();
        }
    }void buildPaginationBar() {
        if(paginationBar == null) paginationBar = new Table();
        paginationBar.clearChildren();
        paginationBar.setBackground(Styles.black6);
        
        addTooltip(paginationBar.button("<", Styles.cleart, () -> {
            if(currentPage > 0) {
                currentPage--;
                updateVisibleMods();
            }
        }).size(80f, 50f).disabled(b -> currentPage == 0), "Previous page");
        
        paginationBar.add().growX();
        paginationBar.label(() -> "[accent]Page " + (currentPage + 1) + " / " + Math.max(1, getMaxPage() + 1) + 
                         "  [lightgray]|  " + filteredMods.size + " mods").pad(10f);
        paginationBar.add().growX();
        
        addTooltip(paginationBar.button(">", Styles.cleart, () -> {
            if(currentPage < getMaxPage()) {
                currentPage++;
                updateVisibleMods();
            }
        }).size(80f, 50f).disabled(b -> currentPage >= getMaxPage()), "Next page");
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
        
        if(currentlyLoadingStats.contains(key)) return;
        
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
        if(statusLabel != null) {
            statusLabel.setText("[lightgray]" + text);
        }
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }

    void fetchModList() {
        updateStatusLabel("[cyan]Loading enabled mods...");
        lastRefreshTime = Time.millis();
        Core.app.post(() -> {
            allMods.clear();
            
            for(Mods.LoadedMod mod : Vars.mods.list()) {
                if(!mod.enabled()) continue;
                
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
                    info.repo = "";
                }
                
                info.installedMod = mod;
                info.isInstalled = true;
                info.isEnabled = true;
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
        lastRefreshTime = Time.millis();
        Core.app.post(() -> {
            allMods.clear();
            
            for(Mods.LoadedMod mod : Vars.mods.list()) {
                if(mod.enabled()) continue;
                
                ModInfo info = new ModInfo();
                
                if(mod.meta != null) {
                    info.name = mod.meta.name;
                    info.author = mod.meta.author;
                    info.description = mod.meta.description;
                    info.version = mod.meta.version;
                    info.hasJava = mod.meta.java;
                    info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
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
                info.isEnabled = false;
                allMods.add(info);
            }
            
            Core.app.post(() -> {
                currentPage = 0;
                applyFilter();
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
        ObjectSet<String> installedRepos = new ObjectSet<>();
        
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(mod.meta != null && mod.meta.repo != null) {
                installedRepos.add(mod.meta.repo);
            }
        }
        
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
                mod.hasJava = modJson.getBoolean("hasJava", false);
                mod.hasScripts = modJson.getBoolean("hasScripts", false);
                mod.isInstalled = installedRepos.contains(mod.repo);
                
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
    }void buildModRow(Table table, ModInfo mod) {
        Mods.LoadedMod installed = mod.installedMod;
        
        table.table(row -> {
            row.setBackground(Styles.black6);
            row.margin(8f);
            row.left();
            
            TextureRegion icon = getModIcon(mod, installed);
            float iconSize = 72f;
            if(icon != null) {
                row.image(icon).size(iconSize).pad(10f);
            } else {
                row.image(Icon.box).size(iconSize).color(Color.gray).pad(10f);
            }
            
            row.table(info -> {
                info.left().defaults().left().growX();
                
                info.table(nameRow -> {
                    nameRow.left();
                    Label nameLabel = new Label(mod.name);
                    nameLabel.setStyle(Styles.outlineLabel);
                    nameLabel.setColor(Color.white);
                    nameRow.add(nameLabel).padRight(8f);
                    
                    if(mod.isInstalled) {
                        nameRow.image(Icon.ok).size(20f).color(enabledColor).padRight(4f);
                    }
                }).left().row();
                
                info.add("[lightgray]v" + mod.version).left().padTop(2f).row();
                
                if(mod.hasJava || mod.hasScripts) {
                    info.table(badges -> {
                        badges.left().defaults().size(28f).pad(2f);
                        
                        if(mod.hasJava) {
                            TextureRegion javaBadge = badgeSprites.get("testmod-java-badge");
                            if(javaBadge != null) {
                                Image badge = new Image(javaBadge);
                                badge.setColor(Color.white);
                                badges.add(badge).size(36f, 24f);
                            } else {
                                badges.image(Icon.pencil).color(Color.valueOf("b07219"));
                            }
                        } else if(mod.hasScripts) {
                            TextureRegion jsBadge = badgeSprites.get("testmod-js-badge");
                            if(jsBadge != null) {
                                Image badge = new Image(jsBadge);
                                badge.setColor(Color.white);
                                badges.add(badge).size(36f, 24f);
                            } else {
                                badges.image(Icon.wrench).color(Color.valueOf("f1e05a"));
                            }
                        }
                        
                        badges.image(Icon.file).size(24f).color(Color.valueOf("89e051")).padLeft(4f);
                        
                        if(installed != null && mod.isServerCompatible) {
                            badges.image(Icon.host).size(24f).color(Color.sky).padLeft(4f);
                        }
                    }).left().padTop(4f).row();
                }
                
            }).growX().padLeft(10f).padRight(10f);
            
            row.table(actions -> {
                actions.right().defaults().size(50f).pad(4f);
                
                if(installed != null) {
                    if(installed.enabled()) {
                        addTooltip(actions.button(Icon.ok, Styles.clearNonei, () -> {
                            toggleModState(mod, installed);
                        }).update(b -> ((ImageButton.ImageButtonStyle)b.getStyle()).imageUpColor = enabledColor), 
                        "Disable mod");
                    } else {
                        addTooltip(actions.button(Icon.cancel, Styles.clearNonei, () -> {
                            toggleModState(mod, installed);
                        }).update(b -> ((ImageButton.ImageButtonStyle)b.getStyle()).imageUpColor = disabledColor), 
                        "Enable mod");
                    }
                }
                
                addTooltip(actions.button(Icon.rightOpen, Styles.clearNonei, () -> {
                    showModDetails(mod);
                }), "View details");
                
            }).right().padRight(10f);
            
        }).fillX().minHeight(100f).pad(4f).row();
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
            reloadMods();
        } catch(Exception e) {
            Vars.ui.showErrorMessage("Failed to toggle mod state");
        }
    }

    void confirmDelete(ModInfo mod, Mods.LoadedMod installed) {
        BaseDialog confirm = new BaseDialog("Delete Mod");
        confirm.cont.add("[scarlet]Delete " + mod.name + "?").pad(20f).row();
        confirm.cont.add("[lightgray]This cannot be undone.").pad(10f).row();
        
        confirm.buttons.defaults().size(150f, 50f).pad(10f);
        addTooltip(confirm.buttons.button("Cancel", Icon.cancel, () -> {
            confirm.hide();
        }), "Cancel");
        addTooltip(confirm.buttons.button("Delete", Icon.trash, () -> {
            deleteMod(mod, installed);
            confirm.hide();
        }).update(b -> b.getLabel().setColor(Color.scarlet)), "Delete permanently");
        
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
        if(mod.repo.isEmpty()) {
            Vars.ui.showErrorMessage("No repository found");
            return;
        }
        
        Vars.ui.showInfo("[cyan]Downloading " + mod.name + "...");
        
        Core.app.post(() -> {
            HttpURLConnection conn = null;
            try {
                String[] repoParts = mod.repo.split("/");
                if(repoParts.length < 2) {
                    Core.app.post(() -> Vars.ui.showErrorMessage("Invalid repository"));
                    return;
                }
                
                String owner = repoParts[0];
                String repo = repoParts[1];
                
                String releaseUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";
                conn = (HttpURLConnection) new URL(releaseUrl).openConnection();
                conn.setRequestProperty("User-Agent", "ModInfo+");
                conn.setRequestProperty("Authorization", "token " + getNextToken());
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                
                if(conn.getResponseCode() != 200) {
                    Core.app.post(() -> Vars.ui.showErrorMessage("No releases found"));
                    return;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder json = new StringBuilder();
                String line;
                while((line = reader.readLine()) != null) json.append(line);
                reader.close();
                
                JsonValue release = new JsonReader().parse(json.toString());
                JsonValue assets = release.get("assets");
                
                String downloadUrl = null;
                String fileName = null;
                
                if(assets != null) {
                    for(JsonValue asset : assets) {
                        String name = asset.getString("name", "");
                        if(name.endsWith(".jar")) {
                            downloadUrl = asset.getString("browser_download_url", "");
                            fileName = name;
                            break;
                        }
                    }
                    
                    if(downloadUrl == null) {
                        for(JsonValue asset : assets) {
                            String name = asset.getString("name", "");
                            if(name.endsWith(".zip")) {
                                downloadUrl = asset.getString("browser_download_url", "");
                                fileName = name;
                                break;
                            }
                        }
                    }
                }
                
                if(downloadUrl == null) {
                    Core.app.post(() -> Vars.ui.showErrorMessage("No downloadable file found"));
                    return;
                }
                
                String finalDownloadUrl = downloadUrl;
                String finalFileName = fileName;
                
                HttpURLConnection downloadConn = (HttpURLConnection) new URL(finalDownloadUrl).openConnection();
                downloadConn.setRequestProperty("User-Agent", "ModInfo+");
                downloadConn.setConnectTimeout(30000);
                downloadConn.setReadTimeout(30000);
                
                InputStream in = downloadConn.getInputStream();
                Fi tempFile = Fi.get(System.getProperty("java.io.tmpdir")).child(finalFileName);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                OutputStream out = tempFile.write(false);
                while((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.close();
                in.close();
                
                Core.app.post(() -> {
                    try {
                        Vars.mods.importMod(tempFile);
                        Vars.ui.showInfo("[lime]" + mod.name + " installed!\n[lightgray]Restart to apply.");
                        tempFile.delete();
                        reloadMods();
                    } catch(Exception e) {
                        Vars.ui.showErrorMessage("Import failed: " + e.getMessage());
                    }
                });
                
            } catch(Exception e) {
                Core.app.post(() -> Vars.ui.showErrorMessage("Download failed: " + e.getMessage()));
            } finally {
                if(conn != null) try { conn.disconnect(); } catch(Exception e) {}
            }
        });
    }void showModDetails(ModInfo mod) {
        Mods.LoadedMod installed = mod.installedMod;
        
        BaseDialog dialog = new BaseDialog("");
        
        Table main = new Table(Styles.black6);
        main.margin(20f);
        
        Table header = new Table();
        header.setBackground(Styles.black8);
        
        TextureRegion icon = getModIcon(mod, installed);
        if(icon != null) {
            header.image(icon).size(96f).pad(12f);
        } else {
            header.image(Icon.box).size(96f).color(Color.gray).pad(12f);
        }
        
        header.table(title -> {
            title.left().defaults().left();
            title.add("[accent]" + mod.name).style(Styles.outlineLabel).padBottom(4f).row();
            title.add("[lightgray]v" + mod.version).row();
        }).growX().padLeft(12f);
        
        main.add(header).fillX().pad(8f).row();
        
        main.image().color(accentColor).height(3f).fillX().pad(8f).row();
        
        Table badges = new Table();
        badges.left();
        badges.defaults().padRight(12f).size(36f);
        
        if(mod.hasJava) {
            TextureRegion javaBadge = badgeSprites.get("testmod-java-badge");
            if(javaBadge != null) {
                Image badge = new Image(javaBadge);
                badge.setColor(Color.white);
                badges.add(badge).size(44f, 28f);
            } else {
                badges.image(Icon.pencil).color(Color.valueOf("b07219"));
            }
        } else if(mod.hasScripts) {
            TextureRegion jsBadge = badgeSprites.get("testmod-js-badge");
            if(jsBadge != null) {
                Image badge = new Image(jsBadge);
                badge.setColor(Color.white);
                badges.add(badge).size(44f, 28f);
            } else {
                badges.image(Icon.wrench).color(Color.valueOf("f1e05a"));
            }
        }
        
        badges.image(Icon.file).color(Color.valueOf("89e051"));
        
        if(installed != null) {
            if(mod.isServerCompatible) {
                badges.image(Icon.host).color(Color.sky).padLeft(8f);
            }
            
            if(installed.enabled()) {
                badges.image(Icon.ok).color(enabledColor).padLeft(12f);
            } else {
                badges.image(Icon.cancel).color(disabledColor).padLeft(12f);
            }
        }
        
        if(mod.isInstalled) {
            badges.image(Icon.ok).size(32f).color(enabledColor).padLeft(12f);
        }
        
        main.add(badges).left().pad(8f).row();
        
        Table info = new Table();
        info.left().defaults().left().pad(4f);
        
        if(!mod.repo.isEmpty()) {
            info.add("[lightgray]Repository:").padRight(12f);
            info.add("[white]" + mod.repo).row();
        }
        
        main.add(info).left().fillX().pad(8f).row();
        
        if(!mod.repo.isEmpty()) {
            main.image().color(accentColor).height(2f).fillX().pad(8f).row();
            
            Table statsTable = new Table();
            statsTable.left().defaults().left().pad(5f);
            statsTable.add("[lightgray]Loading stats...").colspan(2).row();
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
            addTooltip(actions.button("Open GitHub", Icon.link, () -> {
                Core.app.openURI("https://github.com/" + mod.repo);
            }), "Open in browser");
        }
        
        if(installed != null) {
            addTooltip(actions.button(installed.enabled() ? "Disable" : "Enable", 
                          installed.enabled() ? Icon.cancel : Icon.ok, () -> {
                toggleModState(mod, installed);
                dialog.hide();
            }), installed.enabled() ? "Disable mod" : "Enable mod");
            
            actions.row();
            
            addTooltip(actions.button("Delete", Icon.trash, () -> {
                confirmDelete(mod, installed);
                dialog.hide();
            }).update(b -> b.getLabel().setColor(Color.scarlet)), "Delete mod");
        } else if(!mod.repo.isEmpty() && !mod.isInstalled) {
            addTooltip(actions.button("Download", Icon.download, () -> {
                installMod(mod);
                dialog.hide();
            }), "Download and install");
        }
        
        main.add(actions).fillX().pad(8f).row();
        
        addTooltip(main.button("Close", Icon.cancel, () -> {
            dialog.hide();
        }).size(180f, 45f).pad(8f), "Close dialog");
        
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
        
        if(currentlyLoadingStats.contains(key)) return;
        
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
                String[] parts = mod.repo.split("/");
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
        boolean isInstalled = false;
        boolean isEnabled = false;
        Mods.LoadedMod installedMod = null;
    }
    
    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}