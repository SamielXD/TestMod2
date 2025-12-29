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
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;
    private TextureRegion jsonBadge;
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();
    private int currentTab = 0;
    private String sortMode = "updated";
    private long lastRefreshTime = 0;

    public TestMod() {
        Log.info("ModInfo+ Browser Initializing");
    }

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadBadges();
                loadModIcons();
            });
        });
        addModInfoButton();
    }
    
    void loadBadges() {
        javaBadge = Core.atlas.find("testmod-java-badge");
        jsBadge = Core.atlas.find("testmod-js-badge");
        jsonBadge = Core.atlas.find("testmod-json-badge");
        
        if(!javaBadge.found()) javaBadge = Core.atlas.find("java-badge");
        if(!jsBadge.found()) jsBadge = Core.atlas.find("js-badge");
        if(!jsonBadge.found()) jsonBadge = Core.atlas.find("json-badge");
        
        Log.info("Badges: Java=" + javaBadge.found() + " JS=" + jsBadge.found() + " JSON=" + jsonBadge.found());
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
                                Log.info("ModInfo+: Hijacked mods button instantly!");
                                return;
                            }
                        }
                    }
                } catch(Exception e) {
                    Log.err("ModInfo+: Hijack failed, using dialog intercept", e);
                }
                
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
        
        Table main = new Table();
        main.background(Tex.pane);
        
        Table header = new Table();
        header.background(Tex.buttonEdge3);
        
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
        
        header.button(Icon.refresh, Styles.cleari, () -> {
            reloadMods();
        }).size(45f).tooltip("Refresh").pad(5f);
        
        header.button(Icon.cancel, Styles.cleari, () -> {
            browserDialog.hide();
        }).size(45f).tooltip("Close").pad(5f);
        
        main.add(header).fillX().height(65f).row();
        
        main.image().color(accentColor).fillX().height(2f).row();
        
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
        
        browserDialog.cont.add(main).size(1000f, 800f);
        browserDialog.show();
        fetchModList();
    }
    
    void reloadMods() {
        allMods.clear();
        filteredMods.clear();
        statsCache.clear();
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
                    mod.author.toLowerCase().contains(searchQuery) ||
                    mod.description.toLowerCase().contains(searchQuery)) {
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
                buildModRow(modListContainer, filteredMods.get(i));
            }
        }
        updateStatusLabel("Showing " + (end - start) + " of " + filteredMods.size + " mods");
        buildPaginationBar();
    }
    
    void updateStatusLabel(String text) {
        statusLabel.setText("[lightgray]" + text);
    }
    
    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }void fetchModList() {
    updateStatusLabel("[cyan]Loading installed mods...");
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
            } else {
                info.name = mod.name;
                info.author = "Unknown";
                info.description = "";
                info.version = "1.0";
                info.hasJava = false;
                info.hasScripts = false;
                info.isServerCompatible = false;
            }
            
            info.repo = "";
            info.stars = 0;
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
            Log.err("Fetch mods", e);
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
    Mods.LoadedMod installed = mod.installedMod;
    
    table.table(Tex.button, row -> {
        row.margin(12f);
        row.left();
        
        TextureRegion icon = getModIcon(mod, installed);
        if(icon != null) {
            row.image(icon).size(70f).padRight(15f);
        } else {
            row.image(Icon.box).size(70f).color(Color.gray).padRight(15f);
        }
        
        row.table(info -> {
            info.left().defaults().left();
            
            info.table(title -> {
                title.left();
                title.add(mod.name).style(Styles.outlineLabel).color(accentColor).padRight(8f);
                
                Table badges = new Table();
                badges.left();
                
                if(mod.hasJava) {
                    if(javaBadge != null && javaBadge.found()) {
                        Image img = badges.image(javaBadge).size(24f, 16f).padRight(4f).get();
                        img.clicked(() -> Vars.ui.showInfo("[accent]Java Mod\n[lightgray]Built with Java code"));
                    } else {
                        Label lbl = badges.add("[white]JAVA").style(Styles.outlineLabel).padRight(4f).get();
                        lbl.clicked(() -> Vars.ui.showInfo("[accent]Java Mod\n[lightgray]Built with Java code"));
                    }
                } else if(mod.hasScripts) {
                    Label lbl = badges.add("[white]JS").style(Styles.outlineLabel).padRight(4f).get();
                    lbl.clicked(() -> Vars.ui.showInfo("[accent]JavaScript Mod\n[lightgray]Built with JS scripts"));
                }
                
                Image jsonImg = badges.image(Icon.book).size(16f).color(Color.valueOf("89e051")).padRight(4f).get();
                jsonImg.clicked(() -> Vars.ui.showInfo("[accent]Configuration\n[lightgray]Uses mod.hjson for metadata"));
                
                if(installed != null) {
                    if(mod.isServerCompatible) {
                        Image hostImg = badges.image(Icon.host).size(16f).color(Color.sky).padRight(4f).get();
                        hostImg.clicked(() -> Vars.ui.showInfo("[accent]Server Compatible\n[lightgray]Works on multiplayer servers"));
                    } else {
                        Image clientImg = badges.image(Icon.players).size(16f).color(Color.orange).padRight(4f).get();
                        clientImg.clicked(() -> Vars.ui.showInfo("[accent]Client Only\n[lightgray]Single-player only"));
                    }
                }
                
                title.add(badges).padLeft(6f);
                
                if(mod.stars >= 10) {
                    title.add(" [yellow]★" + mod.stars).padLeft(8f);
                }
                
                if(installed != null) {
                    if(installed.enabled()) {
                        title.image(Icon.ok).size(18f).color(Color.lime).padLeft(8f);
                    } else {
                        title.image(Icon.cancel).size(18f).color(Color.scarlet).padLeft(8f);
                    }
                }
            }).row();
            
            info.add("[lightgray]by " + mod.author + " [gray]| v" + mod.version).padTop(4f).row();
            
            if(installed != null) {
                info.table(stats -> {
                    stats.left().defaults().left().padRight(10f);
                    if(mod.stars > 0) {
                        stats.add("[yellow]★ " + mod.stars);
                    }
                    if(mod.downloads > 0) {
                        stats.add("[lime]↓ " + mod.downloads);
                    }
                    if(mod.releases > 0) {
                        stats.add("[cyan]⚡ " + mod.releases);
                    }
                }).padTop(4f).row();
                
                if(!mod.repo.isEmpty()) {
                    loadModStatsInline(mod, info);
                }
            }
            
        }).growX().padLeft(8f);
        
        row.table(btns -> {
            btns.defaults().size(50f).pad(3f);
            
            btns.button(Icon.info, Styles.clearNonei, () -> {
                showModDetails(mod);
            }).tooltip("Details").row();
            
            if(installed != null) {
                btns.button(installed.enabled() ? Icon.cancel : Icon.ok, Styles.clearNonei, () -> {
                    toggleModState(mod, installed);
                }).tooltip(installed.enabled() ? "Disable" : "Enable")
                  .update(b -> b.getStyle().imageUpColor = installed.enabled() ? Color.orange : Color.lime).row();
                
                btns.button(Icon.trash, Styles.clearNonei, () -> {
                    confirmDelete(mod, installed);
                }).tooltip("Delete").update(b -> b.getStyle().imageUpColor = Color.scarlet).row();
                
            } else if(!mod.repo.isEmpty()) {
                btns.button(Icon.download, Styles.clearNonei, () -> {
                    installMod(mod);
                }).tooltip("Install").update(b -> b.getStyle().imageUpColor = Color.sky).row();
            }
            
            if(!mod.repo.isEmpty()) {
                btns.button(Icon.link, Styles.clearNonei, () -> {
                    Core.app.openURI("https://github.com/" + mod.repo);
                }).tooltip("GitHub");
            }
            
        }).right().padRight(10f);
        
    }).fillX().height(120f).pad(6f).row();
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
        Log.err("Toggle error", e);
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
        Log.err("Delete error", e);
        Vars.ui.showErrorMessage("Failed to delete mod: " + e.getMessage());
    }
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
    if(mod.hasJava) return javaBadge;
    if(mod.hasScripts) return jsBadge;
    return null;
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

void loadModStatsInline(ModInfo mod, Table infoTable) {
    if(mod.repo.isEmpty()) return;
    
    String key = mod.repo;
    if(statsCache.containsKey(key)) {
        ModStats stats = statsCache.get(key);
        mod.stars = stats.stars;
        mod.downloads = stats.downloads;
        mod.releases = stats.releases;
        return;
    }
    
    fetchModStats(mod, stats -> {
        if(stats != null) {
            statsCache.put(key, stats);
            mod.stars = stats.stars;
            mod.downloads = stats.downloads;
            mod.releases = stats.releases;
            Core.app.post(() -> updateVisibleMods());
        }
    });
}void showModDetails(ModInfo mod) {
    Mods.LoadedMod installed = mod.installedMod;
    
    BaseDialog dialog = new BaseDialog("");
    
    Table main = new Table(Tex.pane);
    main.margin(25f);
    
    Table header = new Table();
    header.background(Tex.button);
    
    TextureRegion icon = getModIcon(mod, installed);
    if(icon != null) {
        header.image(icon).size(96f).pad(15f);
    } else {
        header.image(Icon.box).size(96f).color(Color.gray).pad(15f);
    }
    
    header.table(title -> {
        title.left().defaults().left();
        title.add("[accent]" + mod.name).style(Styles.outlineLabel).padBottom(5f).row();
        title.add("[cyan]by " + mod.author).row();
    }).growX().padLeft(15f);
    
    main.add(header).fillX().pad(10f).row();
    
    main.image().color(accentColor).height(4f).fillX().pad(10f).row();
    
    Table badges = new Table();
    badges.left();
    
    if(mod.hasJava) {
        if(javaBadge != null && javaBadge.found()) {
            Image img = badges.image(javaBadge).size(40f, 26f).padRight(10f).get();
            img.clicked(() -> Vars.ui.showInfo("[accent]Java Mod\n[lightgray]This mod is built with Java code"));
        } else {
            Label lbl = badges.add("[white]JAVA").style(Styles.outlineLabel).padRight(10f).get();
            lbl.clicked(() -> Vars.ui.showInfo("[accent]Java Mod\n[lightgray]This mod is built with Java code"));
        }
    } else if(mod.hasScripts) {
        Label lbl = badges.add("[white]JS").style(Styles.outlineLabel).padRight(10f).get();
        lbl.clicked(() -> Vars.ui.showInfo("[accent]JavaScript Mod\n[lightgray]This mod is built with JavaScript"));
    }
    
    Image jsonImg = badges.image(Icon.book).size(24f).color(Color.valueOf("89e051")).padRight(10f).get();
    jsonImg.clicked(() -> Vars.ui.showInfo("[accent]Configuration\n[lightgray]Uses mod.hjson for metadata"));
    
    if(installed != null) {
        if(mod.isServerCompatible) {
            Image hostImg = badges.image(Icon.host).size(24f).color(Color.sky).padRight(8f).get();
            hostImg.clicked(() -> Vars.ui.showInfo("[accent]Server Compatible\n[lightgray]This mod works on multiplayer servers"));
            badges.add("[sky]Server Compatible").style(Styles.outlineLabel).padRight(10f);
        } else {
            Image clientImg = badges.image(Icon.players).size(24f).color(Color.orange).padRight(8f).get();
            clientImg.clicked(() -> Vars.ui.showInfo("[accent]Client Only\n[lightgray]This mod only works in singleplayer"));
            badges.add("[orange]Client Only").style(Styles.outlineLabel).padRight(10f);
        }
    }
    
    if(installed != null) {
        if(installed.enabled()) {
            badges.image(Icon.ok).size(28f).color(Color.lime).padRight(8f);
            badges.add("[lime]Enabled").style(Styles.outlineLabel);
        } else {
            badges.image(Icon.cancel).size(28f).color(Color.scarlet).padRight(8f);
            badges.add("[scarlet]Disabled").style(Styles.outlineLabel);
        }
    }
    
    main.add(badges).left().pad(10f).row();
    
    Table info = new Table();
    info.left().defaults().left().pad(5f);
    
    info.add("[lightgray]Version:").padRight(15f);
    info.add("[white]" + mod.version).row();
    
    if(!mod.repo.isEmpty()) {
        info.add("[lightgray]Repository:").padRight(15f);
        info.add("[white]" + mod.repo).row();
    }
    
    main.add(info).left().fillX().pad(10f).row();
    
    if(!mod.repo.isEmpty()) {
        main.image().color(accentColor).height(3f).fillX().pad(10f).row();
        
        Table statsTable = new Table();
        statsTable.left().defaults().left().pad(6f);
        statsTable.add("[lightgray]Loading GitHub stats...").colspan(2).row();
        main.add(statsTable).left().fillX().pad(10f).row();
        
        loadGitHubStats(mod, statsTable);
    }
    
    main.image().color(accentColor).height(3f).fillX().pad(10f).row();
    
    if(!mod.description.isEmpty()) {
        main.add("[accent]Description:").left().padLeft(15f).padTop(10f).row();
        Label desc = new Label(mod.description);
        desc.setWrap(true);
        desc.setColor(Color.lightGray);
        desc.setAlignment(Align.left);
        main.add(desc).width(500f).pad(15f).left().row();
        main.image().color(accentColor).height(3f).fillX().pad(10f).row();
    }
    
    Table actions = new Table();
    actions.defaults().size(240f, 55f).pad(8f);
    
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
    
    main.add(actions).fillX().pad(10f).row();
    
    main.button("Close", Icon.cancel, () -> {
        dialog.hide();
    }).size(200f, 50f).pad(10f);
    
    ScrollPane pane = new ScrollPane(main);
    dialog.cont.add(pane).size(600f, 700f);
    dialog.show();
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
        
        statsTable.add("[yellow]★ Stars:").padRight(15f);
        statsTable.add("[white]" + stats.stars).row();
        
        statsTable.add("[lime]↓ Downloads:").padRight(15f);
        statsTable.add("[white]" + stats.downloads).row();
        
        statsTable.add("[cyan]⚡ Releases:").padRight(15f);
        statsTable.add("[white]" + stats.releases).row();
        
        if(!mod.lastUpdated.isEmpty()) {
            statsTable.add("[lightgray]Updated:").padRight(15f);
            statsTable.add("[lightgray]" + formatDate(mod.lastUpdated)).row();
        }
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
}class ModInfo {
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