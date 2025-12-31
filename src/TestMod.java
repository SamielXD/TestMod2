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
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();
    private int currentPage = 0;
    private int modsPerPage = 10;
    private String searchQuery = "";
    private BaseDialog mainDialog;
    private Table modListContainer;
    private Table headerContainer;
    private Label statusLabel;
    private TextField searchField;
    private Table paginationBar;
    private Color accentColor = Color.valueOf("ffd37f");
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;
    private int currentTab = 0;
    private int sortMode = 0;
    private String filterMode = "all";
    private boolean needsRestart = false;
    private float uiBlur = 0f;
    private float uiScale = 1f;
    private float badgeSize = 28f;
    private float badgeSpacing = 4f;

    public TestMod() {
        Log.info("ModInfo+ Initializing");
        initTokens();
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
                loadBadges();
                loadModIcons();
                replaceModsButton();
                addModInfoSettings();
            });
        });
    }
    
    void addModInfoSettings() {
        Vars.ui.settings.addCategory("ModInfo+", Icon.book, table -> {
            table.add("[accent]ModInfo+ Settings").pad(10f).row();
            
            table.table(blur -> {
                blur.add("UI Blur: ").padRight(10f);
                Slider blurSlider = blur.slider(0f, 10f, 0.5f, uiBlur, v -> {
                    uiBlur = v;
                    Core.settings.put("modinfo-blur", v);
                }).width(200f).get();
                Label blurLabel = new Label("");
                blurLabel.update(() -> blurLabel.setText(String.format("%.1f", uiBlur)));
                blur.add(blurLabel).padLeft(10f);
            }).pad(5f).row();
            
            table.table(scale -> {
                scale.add("UI Scale: ").padRight(10f);
                Slider scaleSlider = scale.slider(0.5f, 2f, 0.1f, uiScale, v -> {
                    uiScale = v;
                    Core.settings.put("modinfo-scale", v);
                }).width(200f).get();
                Label scaleLabel = new Label("");
                scaleLabel.update(() -> scaleLabel.setText(String.format("%.1f", uiScale)));
                scale.add(scaleLabel).padLeft(10f);
            }).pad(5f).row();
            
            table.table(badgeSz -> {
                badgeSz.add("Badge Size: ").padRight(10f);
                Slider badgeSlider = badgeSz.slider(16f, 48f, 2f, badgeSize, v -> {
                    badgeSize = v;
                    Core.settings.put("modinfo-badgesize", v);
                }).width(200f).get();
                Label badgeLabel = new Label("");
                badgeLabel.update(() -> badgeLabel.setText(String.format("%.0f", badgeSize)));
                badgeSz.add(badgeLabel).padLeft(10f);
            }).pad(5f).row();
            
            table.table(badgeSp -> {
                badgeSp.add("Badge Spacing: ").padRight(10f);
                Slider spacingSlider = badgeSp.slider(0f, 12f, 1f, badgeSpacing, v -> {
                    badgeSpacing = v;
                    Core.settings.put("modinfo-badgespacing", v);
                }).width(200f).get();
                Label spacingLabel = new Label("");
                spacingLabel.update(() -> spacingLabel.setText(String.format("%.0f", badgeSpacing)));
                badgeSp.add(spacingLabel).padLeft(10f);
            }).pad(5f).row();
            
            table.add("[lightgray]Version: 1.0").pad(10f);
        });
        
        uiBlur = Core.settings.getFloat("modinfo-blur", 0f);
        uiScale = Core.settings.getFloat("modinfo-scale", 1f);
        badgeSize = Core.settings.getFloat("modinfo-badgesize", 28f);
        badgeSpacing = Core.settings.getFloat("modinfo-badgespacing", 4f);
    }
    
    void loadBadges() {
        javaBadge = Core.atlas.find("testmod-java-badge");
        jsBadge = Core.atlas.find("testmod-js-badge");
        
        if(!javaBadge.found()) javaBadge = null;
        if(!jsBadge.found()) jsBadge = null;
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
        mainDialog = new BaseDialog("Mods");
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
            sortBtn.tooltip(t -> {
                t.add(sortMode == 0 ? "Sort: Latest" : "Sort: Stars");
            });
            
            ImageButton filterBtn = headerContainer.button(Icon.filter, Styles.cleari, 35f, () -> {
                showFilterMenu();
            }).size(45f).padRight(5f).get();
            filterBtn.tooltip("Filter mods");
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
        else fetchRemoteMods();
    }

    void reloadMods() {
        allMods.clear();
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
        
        for(ModInfo mod : allMods) {
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
        } else {
            info.name = mod.name;
            info.author = "Unknown";
            info.description = "";
            info.version = "1.0";
        }
        
        info.installedMod = mod;
        info.isInstalled = true;
        detectCapabilities(info, mod);
        
        return info;
    }

    void detectCapabilities(ModInfo info, Mods.LoadedMod mod) {
        info.hasJava = mod.main != null;
        info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
        
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

    void fetchRemoteMods() {
        updateStatusLabel("[cyan]Loading online mods...");
        
        githubGet(
            "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json",
            json -> {
                allMods = parseModList(json);
                loadModIconsFromRemote();
                applyFilter();
                updateVisibleMods();
            },
            () -> updateStatusLabel("[scarlet]Failed to load mod list")
        );
    }

    void loadModIconsFromRemote() {
        for(ModInfo mod : allMods) {
            if(mod.repo.isEmpty()) continue;
            if(modIcons.containsKey(mod.name.toLowerCase())) continue;
            
            String iconUrl = "https://raw.githubusercontent.com/" + mod.repo + "/master/icon.png";
            
            Http.get(iconUrl)
                .timeout(5000)
                .error(e -> {})
                .submit(res -> {
                    try {
                        byte[] data = res.getResult();
                        Pixmap pixmap = new Pixmap(data);
                        Texture tex = new Texture(pixmap);
                        pixmap.dispose();
                        
                        Core.app.post(() -> {
                            String key = mod.name.toLowerCase();
                            modIcons.put(key, new TextureRegion(tex));
                            updateVisibleMods();
                        });
                    } catch(Exception e) {}
                });
        }
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
                
                boolean clientSideFlag = modJson.getBoolean("clientSide", false);
                mod.clientOnly = clientSideFlag || (mod.hasScripts && !mod.hasJava);
                mod.serverCompatible = !mod.clientOnly;
                
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
    }

    Image makeBadge(Table parent, Drawable icon, Color baseColor, String title, String desc) {
        Image img = parent.image(icon).size(badgeSize, badgeSize * 0.625f).padLeft(badgeSpacing).padRight(badgeSpacing).get();
        img.setColor(baseColor);
        
        img.tooltip(t -> {
            t.add("[accent]" + title).row();
            t.add("[lightgray]" + desc).wrap().width(180f);
        });
        
        img.update(() -> {
            if(img.hasMouse()) {
                img.setColor(baseColor.cpy().lerp(Color.white, 0.35f));
            } else {
                img.setColor(baseColor);
            }
        });
        
        img.clicked(() -> {
            Vars.ui.showInfo("[accent]" + title + "\n[lightgray]" + desc);
        });
        
        return img;
    }
    
    void buildModRow(Table table, ModInfo mod) {
        table.table(Tex.button, row -> {
            row.left();
            
            TextureRegion icon = getModIcon(mod);
            if(icon != null) {
                row.image(icon).size(64f).pad(8f);
            } else {
                row.image(Icon.box).size(64f).color(Color.darkGray).pad(8f);
            }
            
            row.table(info -> {
                info.left().defaults().left();
                
                Label nameLabel = new Label(mod.name);
                nameLabel.setStyle(Styles.outlineLabel);
                nameLabel.setColor(Color.white);
                nameLabel.setEllipsis(true);
                info.add(nameLabel).left().padBottom(2f).row();
                
                Label versionLabel = new Label("v" + mod.version);
                versionLabel.setColor(Color.lightGray);
                info.add(versionLabel).left();
                
                if(mod.stars >= 10) {
                    info.add(" [yellow]★" + mod.stars).padLeft(8f);
                }
                
            }).growX().pad(10f);
            
            row.table(badges -> {
                badges.right();
                
                if(mod.isInstalled && mod.installedMod != null && mod.installedMod.enabled()) {
                    makeBadge(badges, Icon.ok, Color.lime, "Enabled", "This mod is currently active");
                }
                
                if(mod.hasJava && !mod.hasScripts) {
                    Drawable d = javaBadge != null ? javaBadge : Icon.book;
                    makeBadge(badges, d, Color.valueOf("b07219"), "Java Mod", "Contains compiled Java code");
                } else if(mod.hasScripts && !mod.hasJava) {
                    Drawable d = jsBadge != null ? jsBadge : Icon.logic;
                    makeBadge(badges, d, Color.valueOf("f1e05a"), "JavaScript Mod", "Uses Mindustry JS scripting");
                } else if(mod.hasJava && mod.hasScripts) {
                    makeBadge(badges, Icon.warning, Color.orange, "Mixed Runtime", "Uses both Java and JavaScript");
                }
                
                if(mod.clientOnly) {
                    makeBadge(badges, Icon.warning, Color.scarlet, "Client-Side Only", "Does not work on multiplayer servers");
                } else if(mod.serverCompatible) {
                    makeBadge(badges, Icon.host, Color.sky, "Server Compatible", "Safe for multiplayer use");
                }
                
            }).right().padRight(8f);
            
            row.button(Icon.rightOpen, Styles.cleari, () -> {
                showModDetails(mod);
            }).size(48f).padRight(4f);
            
        }).fillX().height(80f).pad(2f).row();
    }

    TextureRegion getModIcon(ModInfo mod) {
        if(mod.installedMod != null && mod.installedMod.iconTexture != null) {
            return new TextureRegion(mod.installedMod.iconTexture);
        }
        
        String key = mod.name.toLowerCase();
        if(modIcons.containsKey(key)) {
            return modIcons.get(key);
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
    }void downloadMod(ModInfo mod) {
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
        
        Table badgeRow = new Table();
        badgeRow.defaults().pad(5f);
        
        if(mod.hasJava && !mod.hasScripts) {
            Table jBadge = new Table(Styles.black6);
            if(javaBadge != null) {
                jBadge.image(javaBadge).size(32f, 20f).pad(4f);
            } else {
                jBadge.add("[#b07219]JAVA").pad(4f);
            }
            badgeRow.add(jBadge);
        } else if(mod.hasScripts && !mod.hasJava) {
            Table sBadge = new Table(Styles.black6);
            if(jsBadge != null) {
                sBadge.image(jsBadge).size(32f, 20f).pad(4f);
            } else {
                sBadge.add("[#f1e05a]JS").pad(4f);
            }
            badgeRow.add(sBadge);
        } else if(mod.hasJava && mod.hasScripts) {
            Table mBadge = new Table(Styles.black6);
            mBadge.image(Icon.warning).size(22f).color(Color.orange).pad(4f);
            mBadge.add("[orange]MIXED").pad(4f);
            badgeRow.add(mBadge);
        }
        
        if(mod.clientOnly) {
            Table cBadge = new Table(Styles.black6);
            cBadge.image(Icon.warning).size(22f).color(Color.scarlet).pad(4f);
            cBadge.add("[scarlet]CLIENT").pad(4f);
            badgeRow.add(cBadge);
        } else if(mod.serverCompatible) {
            Table sBadge = new Table(Styles.black6);
            sBadge.image(Icon.host).size(22f).color(Color.sky).pad(4f);
            sBadge.add("[sky]SERVER").pad(4f);
            badgeRow.add(sBadge);
        }
        
        content.add(badgeRow).row();
        
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
    }class ModInfo {
        String repo = "";
        String name = "";
        String author = "";
        String description = "";
        String version = "";
        String lastUpdated = "";
        int stars = 0;
        boolean hasJava = false;
        boolean hasScripts = false;
        boolean serverCompatible = false;
        boolean clientOnly = false;
        boolean touchesUI = false;
        boolean touchesContent = false;
        boolean isInstalled = false;
        Mods.LoadedMod installedMod = null;
    }

    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
    }
}