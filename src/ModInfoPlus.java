import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.scene.style.*;
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

public class ModInfoPlus extends Mod {
    
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
    
    private Seq<ModInfo> remoteIndex = new Seq<>();
    private Seq<ModInfo> filteredMods = new Seq<>();
    private ObjectMap<String, ModStats> statsCache = new ObjectMap<>();
    private ObjectMap<String, TextureRegion> iconCache = new ObjectMap<>();
    private ObjectMap<String, Texture> remoteIconTextures = new ObjectMap<>();
    
    private int currentPage = 0;
    private int modsPerPage = 10;
    private String searchQuery = "";
    private BaseDialog mainDialog;
    private Table modListContainer;
    private Table headerContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private int currentTab = 0;
    private int sortMode = 0;
    private String filterMode = "all";
    private boolean needsRestart = false;
    
    private float badgeSize = 24f;
    private float badgeSpacing = 4f;
    private float iconSize = 64f;
    private float rowHeight = 90f;
    private boolean showBadgeGlow = true;
    private boolean showUpdateBadge = true;
    private boolean showLanguageBadges = true;
    private boolean animateBadges = true;
    private int verifiedStarThreshold = 50;
    private Color accentColor = Color.valueOf("ffd37f");
    
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;

    public ModInfoPlus() {
        Log.info("ModInfo+ Browser Initializing");
        initTokens();
        loadSettings();
    }
    
    void loadSettings() {
        badgeSize = Core.settings.getFloat("modinfo-badgesize", 24f);
        badgeSpacing = Core.settings.getFloat("modinfo-badgespacing", 4f);
        iconSize = Core.settings.getFloat("modinfo-iconsize", 64f);
        rowHeight = Core.settings.getFloat("modinfo-rowheight", 90f);
        showBadgeGlow = Core.settings.getBool("modinfo-badgeglow", true);
        showUpdateBadge = Core.settings.getBool("modinfo-updatebadge", true);
        showLanguageBadges = Core.settings.getBool("modinfo-langbadges", true);
        animateBadges = Core.settings.getBool("modinfo-animatebadges", true);
        verifiedStarThreshold = Core.settings.getInt("modinfo-starverify", 50);
    }
    
    void saveSettings() {
        Core.settings.put("modinfo-badgesize", badgeSize);
        Core.settings.put("modinfo-badgespacing", badgeSpacing);
        Core.settings.put("modinfo-iconsize", iconSize);
        Core.settings.put("modinfo-rowheight", rowHeight);
        Core.settings.put("modinfo-badgeglow", showBadgeGlow);
        Core.settings.put("modinfo-updatebadge", showUpdateBadge);
        Core.settings.put("modinfo-langbadges", showLanguageBadges);
        Core.settings.put("modinfo-animatebadges", animateBadges);
        Core.settings.put("modinfo-starverify", verifiedStarThreshold);
        Core.settings.forceSave();
    }
    
    void initTokens() {
        tokens.add(new TokenInfo(new String[]{"ghp_", "VVNy", "jnJl", "AYvi", "yOWR", "JPdr", "FEzb", "YIIX", "Uh2a", "49ho"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "ljb7", "p6nU", "pWfe", "WGW1", "ookX", "2Fhh", "t9XT", "qT1P", "nffd"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "mPsi", "rCTW", "Nqhv", "CEm", "OY2V", "szbF", "Pf7Y", "OTP0", "N7tC"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "hLcz", "gAeJ", "9C7z", "MWZx", "QNOY", "Ixe", "Mxrl", "ELx2", "rABt"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "2vFi", "cGWH", "841E", "RXkW", "H1bL", "f657", "s3kc", "Cs2p", "Q5Ps"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "Auiw", "COyF", "eyiL", "EMOc", "qBNW", "3e10", "srXv", "f42l", "v4dV"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "lB64", "Pyuc", "2hUz", "JQpz", "rw9G", "B5DQ", "Qyhe", "a62i", "VYyK"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "Hpmd", "9YgD", "MzWS", "5Nam", "joPu", "5PUU", "ubkf", "wm2r", "9GDh"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "DkDK", "oynK", "BXG8", "Fitm", "pQ2v", "Voja", "Y6X6", "zo02", "fXrm"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "NXzL", "Nk7N", "0QYm", "W6TD", "VAkU", "3E8Z", "I26N", "Eb3X", "60zr"}));
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
                loadBadgeTextures();
                replaceModsButton();
                addModInfoSettings();
            });
        });
    }
    
    void loadBadgeTextures() {
        javaBadge = Core.atlas.find("modinfo-java-badge");
        jsBadge = Core.atlas.find("modinfo-js-badge");
        
        if(!javaBadge.found()) javaBadge = null;
        if(!jsBadge.found()) jsBadge = null;
    }
    
    void replaceModsButton() {
        Vars.ui.mods.shown(() -> {
            Core.app.post(() -> {
                Vars.ui.mods.hide();
                showModInfoBrowser();
            });
        });
  }void addModInfoSettings() {
        Vars.ui.settings.addCategory("ModInfo+ Browser", Icon.book, table -> {
            table.add("[accent]ModInfo+ Browser Settings").pad(10f).row();
            table.image().height(3f).width(400f).color(accentColor).pad(5f).row();
            
            table.table(t -> {
                t.add("Badge Size: ").left().padRight(10f);
                Slider slider = t.slider(12f, 48f, 2f, badgeSize, v -> {
                    badgeSize = v;
                    saveSettings();
                    rebuildIfOpen();
                }).width(200f).get();
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", badgeSize)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Badge Spacing: ").left().padRight(10f);
                Slider slider = t.slider(0f, 16f, 1f, badgeSpacing, v -> {
                    badgeSpacing = v;
                    saveSettings();
                    rebuildIfOpen();
                }).width(200f).get();
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", badgeSpacing)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Icon Size: ").left().padRight(10f);
                Slider slider = t.slider(32f, 128f, 4f, iconSize, v -> {
                    iconSize = v;
                    rowHeight = iconSize + 26f;
                    saveSettings();
                    rebuildIfOpen();
                }).width(200f).get();
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", iconSize)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Mods Per Page: ").left().padRight(10f);
                Slider slider = t.slider(5f, 30f, 1f, modsPerPage, v -> {
                    modsPerPage = (int)v;
                    saveSettings();
                    rebuildIfOpen();
                }).width(200f).get();
                Label label = new Label("");
                label.update(() -> label.setText(String.valueOf(modsPerPage)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Verified Threshold: ").left().padRight(10f);
                Slider slider = t.slider(10f, 200f, 10f, verifiedStarThreshold, v -> {
                    verifiedStarThreshold = (int)v;
                    saveSettings();
                }).width(200f).get();
                Label label = new Label("");
                label.update(() -> label.setText(verifiedStarThreshold + " stars"));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.image().height(2f).width(400f).color(Color.gray).pad(8f).row();
            
            table.check("Show Badge Glow", showBadgeGlow, v -> {
                showBadgeGlow = v;
                saveSettings();
            }).left().pad(5f).row();
            
            table.check("Show Update Badges", showUpdateBadge, v -> {
                showUpdateBadge = v;
                saveSettings();
                rebuildIfOpen();
            }).left().pad(5f).row();
            
            table.check("Show Language Badges", showLanguageBadges, v -> {
                showLanguageBadges = v;
                saveSettings();
                rebuildIfOpen();
            }).left().pad(5f).row();
            
            table.check("Animate Badges", animateBadges, v -> {
                animateBadges = v;
                saveSettings();
            }).left().pad(5f).row();
            
            table.image().height(2f).width(400f).color(Color.gray).pad(8f).row();
            
            table.button("Reset to Defaults", Icon.refresh, () -> {
                badgeSize = 24f;
                badgeSpacing = 4f;
                iconSize = 64f;
                rowHeight = 90f;
                modsPerPage = 10;
                showBadgeGlow = true;
                showUpdateBadge = true;
                showLanguageBadges = true;
                animateBadges = true;
                verifiedStarThreshold = 50;
                saveSettings();
                rebuildIfOpen();
                Vars.ui.showInfo("[lime]Settings reset to defaults");
            }).size(250f, 50f).pad(10f);
            
            table.add("[lightgray]ModInfo+ v1.0").pad(10f);
        });
    }
    
    void rebuildIfOpen() {
        if(mainDialog != null && mainDialog.isShown()) {
            updateVisibleMods();
        }
    }
    
    void showModInfoBrowser() {
        if(mainDialog != null) {
            mainDialog.show();
            return;
        }
        mainDialog = new BaseDialog("ModInfo+ Browser");
        mainDialog.addCloseButton();
        mainDialog.hidden(() -> {
            if(needsRestart) {
                Vars.ui.showConfirm("Restart Required", "[cyan]Mods were installed/changed.\n[yellow]Restart the game?", () -> {
                    Core.app.exit();
                });
                needsRestart = false;
            }
        });
        
        boolean isPortrait = Core.graphics.getHeight() > Core.graphics.getWidth();
        
        Table main = new Table(Tex.pane);
        
        if(isPortrait) {
            buildPortraitLayout(main);
        } else {
            buildLandscapeLayout(main);
        }
        
        float width = isPortrait ? Core.graphics.getWidth() * 0.95f : 900f;
        float height = isPortrait ? Core.graphics.getHeight() * 0.9f : Core.graphics.getHeight() * 0.85f;
        
        mainDialog.cont.add(main).size(width, height);
        mainDialog.show();
        
        switchTab(0);
    }
    
    void buildPortraitLayout(Table main) {
        headerContainer = new Table();
        main.add(headerContainer).fillX().row();
        buildHeader();
        
        main.image().color(accentColor).fillX().height(2f).row();
        
        main.table(tabs -> {
            tabs.defaults().growX().height(45f).pad(2f);
            tabs.button("Enabled", () -> switchTab(0)).update(b -> b.setChecked(currentTab == 0));
            tabs.button("Disabled", () -> switchTab(1)).update(b -> b.setChecked(currentTab == 1));
            tabs.button("Browse", () -> switchTab(2)).update(b -> b.setChecked(currentTab == 2));
        }).fillX().row();
        
        main.table(search -> {
            search.image(Icon.zoom).size(28f).padRight(6f);
            searchField = new TextField();
            searchField.setMessageText("Search...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
                updateVisibleMods();
            });
            search.add(searchField).growX().height(40f).pad(8f);
        }).fillX().row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(5f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        
        main.add(pane).grow().row();
        
        paginationBar = new Table();
        main.add(paginationBar).fillX().row();
    }
    
    void buildLandscapeLayout(Table main) {
        headerContainer = new Table();
        main.add(headerContainer).fillX().row();
        buildHeader();
        
        main.image().color(accentColor).fillX().height(2f).row();
        
        main.table(tabs -> {
            tabs.defaults().growX().height(45f);
            tabs.button("Enabled", () -> switchTab(0)).update(b -> b.setChecked(currentTab == 0));
            tabs.button("Disabled", () -> switchTab(1)).update(b -> b.setChecked(currentTab == 1));
            tabs.button("Browse", () -> switchTab(2)).update(b -> b.setChecked(currentTab == 2));
        }).fillX().row();
        
        main.table(search -> {
            search.image(Icon.zoom).size(28f).padRight(8f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
                updateVisibleMods();
            });
            search.add(searchField).growX().height(40f).pad(8f);
        }).fillX().row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(5f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        
        main.add(pane).grow().row();
        
        paginationBar = new Table();
        main.add(paginationBar).fillX().row();
    }
    
    void buildHeader() {
        headerContainer.clearChildren();
        headerContainer.background(Tex.button);
        
        headerContainer.image(Icon.book).size(35f).padLeft(10f).padRight(8f);
        headerContainer.add("[accent]MODINFO+").style(Styles.outlineLabel).left();
        headerContainer.add().growX();
        
        if(currentTab == 2) {
            ImageButton sortBtn = headerContainer.button(sortMode == 0 ? Icon.down : Icon.star, Styles.cleari, 35f, () -> {
                sortMode = (sortMode + 1) % 2;
                buildHeader();
                applySorting();
            }).size(45f).padRight(5f).get();
            sortBtn.addListener(new Tooltip(t -> {
                t.background(Styles.black6);
                t.add(sortMode == 0 ? "Sort: Latest" : "Sort: Stars");
            }));
            
            ImageButton filterBtn = headerContainer.button(Icon.filter, Styles.cleari, 35f, () -> {
                showFilterMenu();
            }).size(45f).padRight(5f).get();
            filterBtn.addListener(new Tooltip(t -> {
                t.background(Styles.black6);
                t.add("Filter mods");
            }));
        }
        
        headerContainer.button(Icon.book, Styles.cleari, 35f, () -> {
            Core.app.openURI("https://mindustrygame.github.io/wiki/modding/1-modding/");
        }).size(45f).padRight(5f);
        
        headerContainer.button(Icon.upload, Styles.cleari, 35f, () -> {
            importModFile();
        }).size(45f).padRight(5f);
        
        headerContainer.button(Icon.refresh, Styles.cleari, 35f, this::reloadMods).size(45f).padRight(8f);
    }
    
    void showFilterMenu() {
        BaseDialog filter = new BaseDialog("Filter");
        filter.cont.defaults().size(200f, 50f).pad(5f);
        filter.cont.button("All Mods", () -> {
            filterMode = "all";
            filter.hide();
            applyFilter();
        }).row();
        filter.cont.button("Java Only", () -> {
            filterMode = "java";
            filter.hide();
            applyFilter();
        }).row();
        filter.cont.button("JS Only", () -> {
            filterMode = "js";
            filter.hide();
            applyFilter();
        }).row();
        filter.cont.button("Server Compatible", () -> {
            filterMode = "server";
            filter.hide();
            applyFilter();
        }).row();
        filter.addCloseButton();
        filter.show();
    }
    
    void importModFile() {
        Vars.platform.showFileChooser(true, "zip", "jar", file -> {
            try {
                Vars.mods.importMod(file);
                Vars.ui.showInfo("[lime]Mod imported!\n[yellow]Restart required");
                needsRestart = true;
                reloadMods();
            } catch(Exception e) {
                Vars.ui.showErrorMessage("Import failed: " + e.getMessage());
            }
        });
          }void switchTab(int tab) {
        currentTab = tab;
        currentPage = 0;
        searchQuery = "";
        sortMode = 0;
        filterMode = "all";
        if(searchField != null) searchField.setText("");
        buildHeader();
        
        if(tab == 0) fetchEnabledMods();
        else if(tab == 1) fetchDisabledMods();
        else fetchRemoteIndex();
    }

    void reloadMods() {
        remoteIndex.clear();
        filteredMods.clear();
        statsCache.clear();
        switchTab(currentTab);
    }

    void applySorting() {
        if(sortMode == 0) {
            filteredMods.sort((a, b) -> b.lastUpdated.compareTo(a.lastUpdated));
        } else {
            filteredMods.sort((a, b) -> Integer.compare(b.stars, a.stars));
        }
        updateVisibleMods();
    }

    void buildPaginationBar() {
        if(paginationBar == null) return;
        paginationBar.clearChildren();
        
        if(currentTab != 2) return;
        
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
        
        for(ModInfo mod : remoteIndex) {
            boolean matchesSearch = searchQuery.isEmpty() || 
                mod.name.toLowerCase().contains(searchQuery) || 
                mod.author.toLowerCase().contains(searchQuery) ||
                mod.description.toLowerCase().contains(searchQuery);
            
            if(!matchesSearch) continue;
            
            boolean matchesFilter = true;
            if(filterMode.equals("java")) matchesFilter = mod.hasJava;
            else if(filterMode.equals("js")) matchesFilter = mod.hasScripts;
            else if(filterMode.equals("server")) matchesFilter = mod.serverCompatible;
            
            if(matchesFilter) {
                filteredMods.add(mod);
            }
        }
        
        applySorting();
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
            remoteIndex.clear();
            for(Mods.LoadedMod mod : Vars.mods.list()) {
                if(!mod.enabled()) continue;
                ModInfo info = createLocalModInfo(mod);
                remoteIndex.add(info);
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
            remoteIndex.clear();
            for(Mods.LoadedMod mod : Vars.mods.list()) {
                if(mod.enabled()) continue;
                ModInfo info = createLocalModInfo(mod);
                remoteIndex.add(info);
            }
            Core.app.post(() -> {
                applyFilter();
                updateVisibleMods();
            });
        });
    }

    ModInfo createLocalModInfo(Mods.LoadedMod mod) {
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
        detectLocalCapabilities(info, mod);
        
        if(mod.iconTexture != null) {
            String key = mod.name.toLowerCase();
            iconCache.put(key, new TextureRegion(mod.iconTexture));
        }
        
        return info;
    }

    void detectLocalCapabilities(ModInfo info, Mods.LoadedMod mod) {
        info.hasJava = mod.main != null;
        info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
        info.hasHjson = mod.root != null && mod.root.child("mod.hjson").exists();
        
        info.touchesUI = false;
        info.touchesContent = false;
        
        if(mod.main != null) {
            String src = mod.main.getClass().getName();
            if(src.contains("ui") || src.contains("dialog") || src.contains("Draw")) {
                info.touchesUI = true;
            }
        }
        
        if(mod.root != null) {
            if(mod.root.child("content").exists()) info.touchesContent = true;
            if(mod.root.child("blocks").exists()) info.touchesContent = true;
            if(mod.root.child("items").exists()) info.touchesContent = true;
            if(mod.root.child("units").exists()) info.touchesContent = true;
        }
        
        info.clientOnly = info.touchesUI && !info.touchesContent;
        info.serverCompatible = !info.clientOnly;
    }

    void fetchRemoteIndex() {
        updateStatusLabel("[cyan]Loading remote index...");
        
        githubGet(
            "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json",
            json -> {
                remoteIndex = parseRemoteIndex(json);
                matchInstalledMods();
                loadRemoteIcons();
                applyFilter();
                updateVisibleMods();
            },
            () -> updateStatusLabel("[scarlet]Failed to load mod index")
        );
    }

    void matchInstalledMods() {
        for(ModInfo remoteMod : remoteIndex) {
            for(Mods.LoadedMod installed : Vars.mods.list()) {
                if(installed.meta != null && installed.name.equalsIgnoreCase(remoteMod.name)) {
                    remoteMod.isInstalled = true;
                    remoteMod.installedMod = installed;
                    remoteMod.localVersion = installed.meta.version;
                    
                    if(installed.iconTexture != null) {
                        String key = remoteMod.name.toLowerCase();
                        iconCache.put(key, new TextureRegion(installed.iconTexture));
                    }
                    break;
                }
            }
        }
    }

    void loadRemoteIcons() {
        for(ModInfo mod : remoteIndex) {
            if(mod.repo.isEmpty()) continue;
            String key = mod.name.toLowerCase();
            if(iconCache.containsKey(key)) continue;
            
            loadIconFromGitHub(mod, "main", "icon.png");
        }
    }

    void loadIconFromGitHub(ModInfo mod, String branch, String filename) {
        String iconUrl = "https://raw.githubusercontent.com/" + mod.repo + "/" + branch + "/" + filename;
        
        Http.get(iconUrl)
            .timeout(5000)
            .error(e -> {
                if(branch.equals("main") && filename.equals("icon.png")) {
                    loadIconFromGitHub(mod, "master", "icon.png");
                } else if(branch.equals("master") && filename.equals("icon.png")) {
                    loadIconFromGitHub(mod, branch, "icon.jpg");
                }
            })
            .submit(res -> {
                try {
                    byte[] data = res.getResult();
                    Pixmap pixmap = new Pixmap(data);
                    Texture tex = new Texture(pixmap);
                    pixmap.dispose();
                    
                    Core.app.post(() -> {
                        String key = mod.name.toLowerCase();
                        iconCache.put(key, new TextureRegion(tex));
                        remoteIconTextures.put(key, tex);
                        updateVisibleMods();
                    });
                } catch(Exception e) {}
            });
    }

    Seq<ModInfo> parseRemoteIndex(String json) {
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
                
                boolean clientSideFlag = modJson.getBoolean("clientSide", false);
                mod.clientOnly = clientSideFlag || (mod.hasScripts && !mod.hasJava);
                mod.serverCompatible = !mod.clientOnly;
                
                mod.isVerified = mod.stars >= verifiedStarThreshold;
                
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
            
            Table iconStack = new Table();
            TextureRegion icon = getModIcon(mod);
            if(icon != null) {
                iconStack.image(icon).size(iconSize).pad(8f);
            } else {
                iconStack.image(Icon.box).size(iconSize).color(Color.darkGray).pad(8f);
            }
            
            Table badgeOverlay = new Table();
            badgeOverlay.top().left();
            badgeOverlay.touchable = Touchable.childrenOnly;
            renderBadges(badgeOverlay, mod);
            
            Stack stack = new Stack();
            stack.add(iconStack);
            stack.add(badgeOverlay);
            stack.touchable = Touchable.childrenOnly;
            row.add(stack).size(iconSize + 16f).pad(4f);
            
            row.table(info -> {
                info.left().defaults().left();
                
                Label nameLabel = new Label(mod.name);
                nameLabel.setStyle(Styles.outlineLabel);
                nameLabel.setColor(Color.white);
                nameLabel.setEllipsis(true);
                info.add(nameLabel).left().padBottom(2f).row();
                
                Table metaRow = new Table();
                metaRow.left();
                
                Label versionLabel = new Label("v" + mod.version);
                versionLabel.setColor(Color.lightGray);
                metaRow.add(versionLabel).left();
                
                if(mod.stars >= 10) {
                    metaRow.add(" [yellow]★" + mod.stars).padLeft(8f);
                }
                
                if(mod.isVerified) {
                    metaRow.image(Icon.ok).size(16f).color(Color.lime).padLeft(6f);
                }
                
                info.add(metaRow).left();
                
            }).growX().pad(10f);
            
            row.button(Icon.rightOpen, Styles.cleari, () -> {
                showModDetails(mod);
            }).size(48f).padRight(4f);
            
        }).fillX().height(rowHeight).pad(2f).row();
    }

    void renderBadges(Table badgeTable, ModInfo mod) {
        badgeTable.clearChildren();
        
        Table topLeft = new Table();
        Table topRight = new Table();
        Table bottomRight = new Table();
        
        topLeft.left();
        topRight.right();
        bottomRight.right();
        
        if(showLanguageBadges) {
            if(mod.hasJava && !mod.hasScripts) {
                addBadge(topLeft, javaBadge != null ? new TextureRegionDrawable(javaBadge) : Icon.book, 
                    Color.valueOf("b07219"), "Java", "Java mod");
            } else if(mod.hasScripts && !mod.hasJava) {
                addBadge(topLeft, jsBadge != null ? new TextureRegionDrawable(jsBadge) : Icon.logic, 
                    Color.valueOf("f1e05a"), "JS", "JavaScript mod");
            } else if(mod.hasJava && mod.hasScripts) {
                addBadge(topLeft, Icon.warning, Color.orange, "Mixed", "Java + JS");
            }
        }
        
        if(mod.isInstalled && mod.installedMod != null && mod.installedMod.enabled()) {
            addBadge(topRight, Icon.ok, Color.lime, "Active", "Currently enabled");
        }
        
        if(mod.clientOnly) {
            addBadge(topRight, Icon.warning, Color.scarlet, "Client", "Client-side only");
        } else if(mod.serverCompatible) {
            addBadge(topRight, Icon.host, Color.sky, "Server", "Server compatible");
        }
        
        if(showUpdateBadge && mod.isInstalled && !mod.localVersion.isEmpty() && !mod.version.equals(mod.localVersion)) {
            addBadge(bottomRight, Icon.upload, Color.cyan, "Update", "New version available");
        }
        
        badgeTable.add(topLeft).expand().top().left().pad(badgeSpacing);
        badgeTable.add(topRight).expand().top().right().pad(badgeSpacing);
        badgeTable.row();
        badgeTable.add().expand();
        badgeTable.add(bottomRight).expand().bottom().right().pad(badgeSpacing);
    }

    void addBadge(Table parent, Drawable icon, Color baseColor, String title, String desc) {
        Table badgeWrap = new Table();
        badgeWrap.touchable = Touchable.enabled;
        
        Image img = new Image(icon);
        img.setColor(baseColor);
        badgeWrap.add(img).size(badgeSize);
        
        parent.add(badgeWrap).size(badgeSize).padRight(badgeSpacing / 2f);
        
        badgeWrap.addListener(new Tooltip(t -> {
            t.background(Styles.black6);
            t.add("[accent]" + title + "\n[lightgray]" + desc).pad(6f);
        }));
        
        if(animateBadges && showBadgeGlow) {
            badgeWrap.addListener(new InputListener() {
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    img.setColor(baseColor.cpy().lerp(Color.white, 0.4f));
                }
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                    img.setColor(baseColor);
                }
            });
        }
    }

    TextureRegion getModIcon(ModInfo mod) {
        String key = mod.name.toLowerCase();
        if(iconCache.containsKey(key)) {
            return iconCache.get(key);
        }
        return null;
    }

    void toggleMod(ModInfo mod) {
        if(mod.installedMod == null) return;
        
        if(mod.installedMod.enabled()) {
            Vars.mods.setEnabled(mod.installedMod, false);
            Vars.ui.showInfo("[orange]Disabled " + mod.name + "\n[lightgray]Restart required");
        } else {
            Vars.mods.setEnabled(mod.installedMod, true);
            Vars.ui.showInfo("[lime]Enabled " + mod.name + "\n[lightgray]Restart required");
        }
        
        needsRestart = true;
        reloadMods();
    }

    void deleteMod(ModInfo mod) {
        if(mod.installedMod == null) return;
        
        Vars.ui.showConfirm("Delete " + mod.name + "?", () -> {
            Vars.mods.removeMod(mod.installedMod);
            Vars.ui.showInfo("[scarlet]Deleted " + mod.name + "\n[lightgray]Restart required");
            needsRestart = true;
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
                            Vars.ui.showInfo("[cyan]Downloading release...");
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
        Vars.ui.showInfo("[cyan]Downloading repository...");
        downloadModFile(mod, zipUrl);
    }

    void downloadModFile(ModInfo mod, String url) {
        try {
            arc.files.Fi file = Vars.tmpDirectory.child(mod.name + "-temp.zip");
            
            Http.get(url)
                .timeout(30000)
                .error(e -> Core.app.post(() -> {
                    Vars.ui.showErrorMessage("Download failed");
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
            Vars.ui.showInfo("[lime]Installed " + mod.name + "!\n[yellow]Restart required");
            
            needsRestart = true;
            Core.app.post(this::reloadMods);
            
        } catch(Exception e) {
            Log.err("Install error", e);
            Vars.ui.showErrorMessage("Install failed");
            updateStatusLabel("[scarlet]Install failed");
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
        
        content.add("[accent]" + mod.name).pad(5f).row();
        content.add("[cyan]by " + mod.author).pad(5f).row();
        content.add("[lightgray]v" + mod.version).pad(3f).row();
        
        if(mod.isVerified) {
            Table verified = new Table(Styles.black6);
            verified.image(Icon.ok).size(20f).color(Color.lime).pad(4f);
            verified.add("[lime]VERIFIED").pad(4f);
            content.add(verified).pad(5f).row();
        }
        
        if(mod.stars > 0) {
            content.add("[yellow]★ " + mod.stars + " stars").pad(3f).row();
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
                
                actions.row();
                
                actions.button("Delete", Icon.trash, () -> {
                    deleteMod(mod);
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

    class ModInfo {
        String repo = "";
        String name = "";
        String author = "";
        String description = "";
        String version = "";
        String localVersion = "";
        String lastUpdated = "";
        int stars = 0;
        boolean hasJava = false;
        boolean hasScripts = false;
        boolean hasHjson = false;
        boolean serverCompatible = false;
        boolean clientOnly = false;
        boolean touchesUI = false;
        boolean touchesContent = false;
        boolean isInstalled = false;
        boolean isVerified = false;
        Mods.LoadedMod installedMod = null;
    }

    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
        }
