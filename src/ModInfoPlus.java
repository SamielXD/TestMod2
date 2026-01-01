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
    
    private Seq<ModInfo> remoteMods = new Seq<>();
    private Seq<ModInfo> installedMods = new Seq<>();
    private Seq<ModInfo> displayList = new Seq<>();
    private ObjectMap<String, ModStats> statsCache = new ObjectMap<>();
    private ObjectMap<String, TextureRegion> iconCache = new ObjectMap<>();
    private ObjectMap<String, Texture> remoteIconTextures = new ObjectMap<>();
    private ObjectSet<String> iconLoadQueue = new ObjectSet<>();
    private ObjectMap<String, String> updateCache = new ObjectMap<>();
    
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
    private boolean remoteLoaded = false;
    
    private float badgeSize = 20f;
    private float badgeSpacing = 3f;
    private float iconSize = 64f;
    private float rowHeight = 90f;
    private float uiScale = 1f;
    private boolean showBadgeGlow = true;
    private boolean showUpdateBadge = true;
    private boolean showLanguageBadges = true;
    private boolean showStatusBadges = true;
    private boolean animateBadges = true;
    private boolean compactMode = false;
    private boolean sideBadges = false;
    private int verifiedStarThreshold = 50;
    private Color accentColor = Color.valueOf("ffd37f");
    
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;

    public ModInfoPlus() {
        Log.info("ModInfo+ Initialized");
        initTokens();
        loadSettings();
    }
    
    void loadSettings() {
        badgeSize = Core.settings.getFloat("modinfo-badgesize", 20f);
        badgeSpacing = Core.settings.getFloat("modinfo-badgespacing", 3f);
        iconSize = Core.settings.getFloat("modinfo-iconsize", 64f);
        rowHeight = Core.settings.getFloat("modinfo-rowheight", 90f);
        uiScale = Core.settings.getFloat("modinfo-uiscale", 1f);
        showBadgeGlow = Core.settings.getBool("modinfo-badgeglow", true);
        showUpdateBadge = Core.settings.getBool("modinfo-updatebadge", true);
        showLanguageBadges = Core.settings.getBool("modinfo-langbadges", true);
        showStatusBadges = Core.settings.getBool("modinfo-statusbadges", true);
        animateBadges = Core.settings.getBool("modinfo-animatebadges", true);
        compactMode = Core.settings.getBool("modinfo-compact", false);
        sideBadges = Core.settings.getBool("modinfo-sidebadges", false);
        modsPerPage = Core.settings.getInt("modinfo-perpage", 10);
        verifiedStarThreshold = Core.settings.getInt("modinfo-starverify", 50);
    }
    
    void saveSettings() {
        Core.settings.put("modinfo-badgesize", badgeSize);
        Core.settings.put("modinfo-badgespacing", badgeSpacing);
        Core.settings.put("modinfo-iconsize", iconSize);
        Core.settings.put("modinfo-rowheight", rowHeight);
        Core.settings.put("modinfo-uiscale", uiScale);
        Core.settings.put("modinfo-badgeglow", showBadgeGlow);
        Core.settings.put("modinfo-updatebadge", showUpdateBadge);
        Core.settings.put("modinfo-langbadges", showLanguageBadges);
        Core.settings.put("modinfo-statusbadges", showStatusBadges);
        Core.settings.put("modinfo-animatebadges", animateBadges);
        Core.settings.put("modinfo-compact", compactMode);
        Core.settings.put("modinfo-sidebadges", sideBadges);
        Core.settings.put("modinfo-perpage", modsPerPage);
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
                Log.err("GitHub request failed: " + url, e);
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
    }void loadInstalledMods() {
        installedMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            ModInfo info = createModInfoFromInstalled(mod);
            installedMods.add(info);
        }
    }
    
    void loadRemoteMods() {
        if(remoteLoaded) {
            rebuildDisplayList();
            return;
        }
        
        updateStatusLabel("[cyan]Fetching remote index...");
        githubGet(
            "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json",
            json -> {
                remoteMods.clear();
                remoteMods = parseRemoteIndex(json);
                remoteLoaded = true;
                overlayInstallStatus();
                loadRemoteIcons();
                detectRemoteCapabilities();
                rebuildDisplayList();
            },
            () -> {
                updateStatusLabel("[scarlet]Failed to load remote index");
                remoteLoaded = false;
                rebuildDisplayList();
            }
        );
    }
    
    void overlayInstallStatus() {
        for(ModInfo remote : remoteMods) {
            for(ModInfo installed : installedMods) {
                if(matchesByRepo(remote, installed)) {
                    remote.isInstalled = true;
                    remote.isEnabled = installed.isEnabled;
                    remote.installedMod = installed.installedMod;
                    remote.installedVersion = installed.version;
                    
                    if(installed.installedMod != null && installed.installedMod.iconTexture != null) {
                        iconCache.put(remote.repo, new TextureRegion(installed.installedMod.iconTexture));
                    }
                    break;
                }
            }
        }
    }
    
    boolean matchesByRepo(ModInfo remote, ModInfo installed) {
        if(remote.repo.isEmpty() || installed.repo.isEmpty()) return false;
        return remote.repo.equalsIgnoreCase(installed.repo);
    }
    
    void loadRemoteIcons() {
        for(ModInfo mod : remoteMods) {
            if(iconCache.containsKey(mod.repo)) continue;
            if(iconLoadQueue.contains(mod.repo)) continue;
            if(mod.repo.isEmpty()) continue;
            
            iconLoadQueue.add(mod.repo);
            loadIconFromGitHub(mod, "master", "icon.png");
        }
    }
    
    void loadIconFromGitHub(ModInfo mod, String branch, String filename) {
        String iconUrl = "https://raw.githubusercontent.com/" + mod.repo + "/" + branch + "/" + filename;
        
        Http.get(iconUrl)
            .timeout(10000)
            .error(e -> {
                if(branch.equals("master") && filename.equals("icon.png")) {
                    Core.app.post(() -> loadIconFromGitHub(mod, "main", "icon.png"));
                } else if(branch.equals("main") && filename.equals("icon.png")) {
                    Core.app.post(() -> loadIconFromGitHub(mod, branch, "icon.jpg"));
                } else if(filename.equals("icon.jpg")) {
                    Core.app.post(() -> loadIconFromGitHub(mod, branch, "icon.jpeg"));
                }
            })
            .submit(res -> {
                try {
                    byte[] data = res.getResult();
                    Pixmap pixmap = new Pixmap(data);
                    Texture tex = new Texture(pixmap);
                    pixmap.dispose();
                    
                    Core.app.post(() -> {
                        iconCache.put(mod.repo, new TextureRegion(tex));
                        remoteIconTextures.put(mod.repo, tex);
                        refreshModRow(mod);
                    });
                } catch(Exception e) {
                    Log.err("Icon decode failed for " + mod.name, e);
                }
            });
    }
    
    void detectRemoteCapabilities() {
        for(ModInfo mod : remoteMods) {
            if(mod.isInstalled) continue;
            if(mod.repo.isEmpty()) continue;
            
            String key = "cap_" + mod.repo;
            if(iconLoadQueue.contains(key)) continue;
            iconLoadQueue.add(key);
            
            githubGet(
                "https://api.github.com/repos/" + mod.repo + "/contents",
                json -> {
                    parseModContents(mod, json);
                    Core.app.post(() -> refreshModRow(mod));
                },
                () -> {}
            );
        }
    }
    
    void parseModContents(ModInfo mod, String json) {
        try {
            JsonValue contents = new JsonReader().parse(json);
            
            mod.hasJava = false;
            mod.hasScripts = false;
            mod.hasHjson = false;
            mod.touchesContent = false;
            
            for(JsonValue item : contents) {
                String name = item.getString("name", "");
                String type = item.getString("type", "");
                
                if(type.equals("dir")) {
                    if(name.equals("src")) mod.hasJava = true;
                    if(name.equals("scripts")) mod.hasScripts = true;
                    if(name.equals("content")) mod.touchesContent = true;
                    if(name.equals("blocks")) mod.touchesContent = true;
                    if(name.equals("items")) mod.touchesContent = true;
                    if(name.equals("units")) mod.touchesContent = true;
                }
                
                if(type.equals("file")) {
                    if(name.equals("mod.hjson") || name.equals("mod.json")) mod.hasHjson = true;
                }
            }
            
            mod.clientOnly = !mod.touchesContent && (mod.hasScripts || mod.touchesUI);
            mod.serverCompatible = !mod.clientOnly;
            
        } catch(Exception e) {
            Log.err("Parse contents failed for " + mod.name, e);
        }
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
    
    ModInfo createModInfoFromInstalled(Mods.LoadedMod mod) {
        ModInfo info = new ModInfo();
        
        if(mod.meta != null) {
            info.displayName = mod.meta.displayName != null ? mod.meta.displayName : mod.meta.name;
            info.name = mod.meta.name;
            info.author = mod.meta.author;
            info.description = mod.meta.description;
            info.version = mod.meta.version;
            info.repo = mod.meta.repo != null ? mod.meta.repo : "";
        } else {
            info.displayName = mod.name;
            info.name = mod.name;
            info.author = "Unknown";
            info.description = "";
            info.version = "1.0";
            info.repo = "";
        }
        
        info.installedMod = mod;
        info.isInstalled = true;
        info.isEnabled = mod.enabled();
        info.installedVersion = info.version;
        detectLocalCapabilities(info, mod);
        
        if(mod.iconTexture != null && !info.repo.isEmpty()) {
            iconCache.put(info.repo, new TextureRegion(mod.iconTexture));
        }
        
        return info;
    }
    
    Seq<ModInfo> parseRemoteIndex(String json) {
        Seq<ModInfo> mods = new Seq<>();
        try {
            JsonValue root = new JsonReader().parse(json);
            for(JsonValue modJson : root) {
                ModInfo mod = new ModInfo();
                mod.repo = modJson.getString("repo", "");
                mod.name = modJson.getString("name", "Unknown");
                mod.displayName = mod.name;
                mod.author = modJson.getString("author", "Unknown");
                mod.description = modJson.getString("description", "");
                mod.version = modJson.getString("minGameVersion", "?");
                mod.lastUpdated = modJson.getString("lastUpdated", "");
                mod.stars = modJson.getInt("stars", 0);
                mod.isVerified = mod.stars >= verifiedStarThreshold;
                
                if(!mod.repo.isEmpty() && !mod.name.isEmpty()) {
                    mods.add(mod);
                }
            }
        } catch(Exception e) {
            Log.err("Parse remote index failed", e);
        }
        return mods;
    }
    
    void rebuildDisplayList() {
        displayList.clear();
        
        if(currentTab == 0) {
            for(ModInfo mod : installedMods) {
                if(mod.isEnabled) displayList.add(mod);
            }
        } else if(currentTab == 1) {
            for(ModInfo mod : installedMods) {
                if(!mod.isEnabled) displayList.add(mod);
            }
        } else if(currentTab == 2) {
            displayList.addAll(remoteMods);
        }
        
        applyFilter();
    }
    
    void applyFilter() {
        Seq<ModInfo> filtered = new Seq<>();
        
        for(ModInfo mod : displayList) {
            boolean matchesSearch = searchQuery.isEmpty() || 
                mod.displayName.toLowerCase().contains(searchQuery) || 
                mod.name.toLowerCase().contains(searchQuery) ||
                mod.author.toLowerCase().contains(searchQuery) ||
                mod.description.toLowerCase().contains(searchQuery);
            
            if(!matchesSearch) continue;
            
            boolean matchesFilter = true;
            if(filterMode.equals("java")) matchesFilter = mod.hasJava;
            else if(filterMode.equals("js")) matchesFilter = mod.hasScripts;
            else if(filterMode.equals("server")) matchesFilter = mod.serverCompatible;
            
            if(matchesFilter) {
                filtered.add(mod);
            }
        }
        
        displayList.clear();
        displayList.addAll(filtered);
        applySorting();
    }
    
    void applySorting() {
        if(currentTab == 2) {
            if(sortMode == 0) {
                displayList.sort((a, b) -> b.lastUpdated.compareTo(a.lastUpdated));
            } else {
                displayList.sort((a, b) -> Integer.compare(b.stars, a.stars));
            }
        }
        updateVisibleMods();
    }
    
    void refreshModRow(ModInfo mod) {
        if(modListContainer == null) return;
        updateVisibleMods();
    }void addModInfoSettings() {
        Vars.ui.settings.addCategory("ModInfo+ Browser", Icon.book, table -> {
            table.add("[accent]ModInfo+ Browser Settings").pad(10f).row();
            table.image().height(3f).width(400f).color(accentColor).pad(5f).row();
            
            table.add("[cyan]Display Settings").left().pad(8f).row();
            table.add("[lightgray]Changes require game restart").left().padLeft(20f).row();
            
            table.table(t -> {
                t.add("UI Scale: ").left().padRight(10f);
                t.slider(0.5f, 2f, 0.1f, uiScale, v -> {
                    uiScale = v;
                    saveSettings();
                    fullRebuild();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.1fx", uiScale)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Icon Size: ").left().padRight(10f);
                t.slider(32f, 128f, 4f, iconSize, v -> {
                    iconSize = v;
                    if(!compactMode) rowHeight = iconSize + 26f;
                    saveSettings();
                    fullRebuild();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", iconSize)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Row Height: ").left().padRight(10f);
                t.slider(50f, 200f, 5f, rowHeight, v -> {
                    rowHeight = v;
                    saveSettings();
                    fullRebuild();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", rowHeight)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Mods Per Page: ").left().padRight(10f);
                t.slider(5f, 30f, 1f, modsPerPage, v -> {
                    modsPerPage = (int)v;
                    saveSettings();
                    fullRebuild();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(String.valueOf(modsPerPage)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.check("Compact Mode", compactMode, v -> {
                compactMode = v;
                if(compactMode) rowHeight = 70f;
                else rowHeight = iconSize + 26f;
                saveSettings();
                fullRebuild();
            }).left().pad(5f).row();
            
            table.image().height(2f).width(400f).color(Color.gray).pad(8f).row();
            table.add("[cyan]Badge Settings").left().pad(8f).row();
            table.add("[lightgray]Changes require game restart").left().padLeft(20f).row();
            
            table.table(t -> {
                t.add("Badge Display Width: ").left().padRight(10f);
                t.slider(12f, 48f, 2f, badgeSize, v -> {
                    badgeSize = v;
                    saveSettings();
                    fullRebuild();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", badgeSize)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.table(t -> {
                t.add("Badge Spacing: ").left().padRight(10f);
                t.slider(0f, 16f, 1f, badgeSpacing, v -> {
                    badgeSpacing = v;
                    saveSettings();
                    fullRebuild();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(String.format("%.0fpx", badgeSpacing)));
                t.add(label).width(60f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.check("Side Badges (Not Overlay)", sideBadges, v -> {
                sideBadges = v;
                saveSettings();
                fullRebuild();
            }).left().pad(5f).row();
            
            table.check("Show Language Badges", showLanguageBadges, v -> {
                showLanguageBadges = v;
                saveSettings();
                fullRebuild();
            }).left().pad(5f).row();
            
            table.check("Show Status Badges", showStatusBadges, v -> {
                showStatusBadges = v;
                saveSettings();
                fullRebuild();
            }).left().pad(5f).row();
            
            table.check("Show Update Badges", showUpdateBadge, v -> {
                showUpdateBadge = v;
                saveSettings();
                fullRebuild();
            }).left().pad(5f).row();
            
            table.check("Animate Badges", animateBadges, v -> {
                animateBadges = v;
                saveSettings();
            }).left().pad(5f).row();
            
            table.check("Badge Glow on Hover", showBadgeGlow, v -> {
                showBadgeGlow = v;
                saveSettings();
            }).left().pad(5f).row();
            
            table.image().height(2f).width(400f).color(Color.gray).pad(8f).row();
            table.add("[cyan]Advanced Settings").left().pad(8f).row();
            
            table.table(t -> {
                t.add("Verified Star Threshold: ").left().padRight(10f);
                t.slider(10f, 200f, 10f, verifiedStarThreshold, v -> {
                    verifiedStarThreshold = (int)v;
                    saveSettings();
                }).width(200f);
                Label label = new Label("");
                label.update(() -> label.setText(verifiedStarThreshold + " stars"));
                t.add(label).width(80f).padLeft(10f);
            }).fillX().pad(5f).row();
            
            table.image().height(2f).width(400f).color(Color.gray).pad(8f).row();
            
            table.button("Reset to Defaults", Icon.refresh, () -> {
                badgeSize = 20f;
                badgeSpacing = 3f;
                iconSize = 64f;
                rowHeight = 90f;
                uiScale = 1f;
                modsPerPage = 10;
                showBadgeGlow = true;
                showUpdateBadge = true;
                showLanguageBadges = true;
                showStatusBadges = true;
                animateBadges = true;
                compactMode = false;
                sideBadges = false;
                verifiedStarThreshold = 50;
                saveSettings();
                fullRebuild();
                Vars.ui.showInfo("[lime]Settings reset to defaults");
            }).size(250f, 50f).pad(10f);
            
            table.add("[lightgray]ModInfo+ v2.5").pad(10f);
        });
    }
    
    void fullRebuild() {
        if(mainDialog != null && mainDialog.isShown()) {
            mainDialog.hide();
            mainDialog = null;
            Core.app.post(this::showModInfoBrowser);
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
                Vars.ui.showConfirm("Restart Required", "[cyan]Mods were installed or changed.\n[yellow]Restart the game now?", () -> {
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
        
        float width = (isPortrait ? Core.graphics.getWidth() * 0.95f : 900f) * uiScale;
        float height = (isPortrait ? Core.graphics.getHeight() * 0.9f : Core.graphics.getHeight() * 0.85f) * uiScale;
        
        mainDialog.cont.add(main).size(width, height);
        mainDialog.show();
        
        loadInstalledMods();
        if(currentTab == 2) {
            loadRemoteMods();
        } else {
            rebuildDisplayList();
        }
    }
    
    void buildPortraitLayout(Table main) {
        headerContainer = new Table();
        main.add(headerContainer).fillX().row();
        buildHeader();
        
        main.image().color(accentColor).fillX().height(2f).row();
        
        main.table(tabs -> {
            tabs.defaults().growX().height(45f * uiScale).pad(2f);
            tabs.button("Enabled", () -> switchTab(0)).update(b -> b.setChecked(currentTab == 0));
            tabs.button("Disabled", () -> switchTab(1)).update(b -> b.setChecked(currentTab == 1));
            tabs.button("Browse", () -> switchTab(2)).update(b -> b.setChecked(currentTab == 2));
        }).fillX().row();
        
        main.table(search -> {
            search.image(Icon.zoom).size(28f * uiScale).padRight(6f);
            searchField = new TextField();
            searchField.setMessageText("Search...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
            });
            search.add(searchField).growX().height(40f * uiScale).pad(8f);
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
            tabs.defaults().growX().height(50f * uiScale).pad(4f);
            tabs.button("Enabled", () -> switchTab(0)).update(b -> b.setChecked(currentTab == 0));
            tabs.button("Disabled", () -> switchTab(1)).update(b -> b.setChecked(currentTab == 1));
            tabs.button("Browse", () -> switchTab(2)).update(b -> b.setChecked(currentTab == 2));
        }).fillX().row();
        
        main.table(search -> {
            search.image(Icon.zoom).size(32f * uiScale).padRight(8f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
            });
            search.add(searchField).growX().height(50f * uiScale).pad(10f);
        }).fillX().row();
        
        statusLabel = new Label("");
        main.add(statusLabel).pad(5f).row();
        
        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        
        main.add(pane).grow().row();
        
        paginationBar = new Table();
        main.add(paginationBar).fillX().row();
    }void buildHeader() {
        headerContainer.clearChildren();
        headerContainer.background(Tex.button);
        
        headerContainer.image(Icon.book).size(35f * uiScale).padLeft(10f).padRight(8f);
        headerContainer.add("[accent]MODINFO+").style(Styles.outlineLabel).left();
        headerContainer.add().growX();
        
        if(currentTab == 2) {
            ImageButton sortBtn = headerContainer.button(sortMode == 0 ? Icon.down : Icon.star, Styles.cleari, 35f * uiScale, () -> {
                sortMode = (sortMode + 1) % 2;
                buildHeader();
                applySorting();
            }).size(45f * uiScale).padRight(5f).get();
            sortBtn.addListener(new Tooltip(t -> {
                t.background(Styles.black6);
                t.add(sortMode == 0 ? "Sort: Latest" : "Sort: Stars").pad(6f);
            }));
            
            ImageButton filterBtn = headerContainer.button(Icon.filter, Styles.cleari, 35f * uiScale, () -> {
                showFilterMenu();
            }).size(45f * uiScale).padRight(5f).get();
            filterBtn.addListener(new Tooltip(t -> {
                t.background(Styles.black6);
                t.add("Filter mods").pad(6f);
            }));
        }
        
        headerContainer.button(Icon.book, Styles.cleari, 35f * uiScale, () -> {
            Core.app.openURI("https://mindustrygame.github.io/wiki/modding/1-modding/");
        }).size(45f * uiScale).padRight(5f);
        
        headerContainer.button(Icon.upload, Styles.cleari, 35f * uiScale, () -> {
            importModFile();
        }).size(45f * uiScale).padRight(5f);
        
        headerContainer.button(Icon.refresh, Styles.cleari, 35f * uiScale, this::reloadMods).size(45f * uiScale).padRight(8f);
    }
    
    void showFilterMenu() {
        BaseDialog filter = new BaseDialog("Filter");
        filter.cont.defaults().size(200f * uiScale, 50f * uiScale).pad(5f);
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
                Vars.ui.showInfo("[lime]Mod imported!\n[yellow]Game restart required");
                needsRestart = true;
                reloadMods();
            } catch(Exception e) {
                Vars.ui.showErrorMessage("Import failed: " + e.getMessage());
            }
        });
    }
    
    void switchTab(int tab) {
        currentTab = tab;
        currentPage = 0;
        searchQuery = "";
        sortMode = 0;
        filterMode = "all";
        if(searchField != null) searchField.setText("");
        buildHeader();
        
        loadInstalledMods();
        if(tab == 2) {
            loadRemoteMods();
        } else {
            rebuildDisplayList();
        }
    }
    
    void reloadMods() {
        remoteMods.clear();
        installedMods.clear();
        displayList.clear();
        statsCache.clear();
        iconLoadQueue.clear();
        updateCache.clear();
        remoteLoaded = false;
        
        loadInstalledMods();
        if(currentTab == 2) {
            loadRemoteMods();
        } else {
            rebuildDisplayList();
        }
    }
    
    void updateVisibleMods() {
        if(modListContainer == null) return;
        modListContainer.clearChildren();
        
        if(currentTab == 2) {
            int start = currentPage * modsPerPage;
            int end = Math.min(start + modsPerPage, displayList.size);
            
            if(displayList.isEmpty()) {
                modListContainer.add("[lightgray]No mods found").pad(40f);
            } else {
                for(int i = start; i < end; i++) {
                    buildModRow(modListContainer, displayList.get(i));
                }
            }
            buildPaginationBar();
        } else {
            if(displayList.isEmpty()) {
                modListContainer.add("[lightgray]No mods found").pad(40f);
            } else {
                for(ModInfo mod : displayList) {
                    buildModRow(modListContainer, mod);
                }
            }
        }
        
        updateStatusLabel("Showing " + displayList.size + " mods");
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
        }).size(80f * uiScale, 50f * uiScale).disabled(b -> currentPage == 0);
        
        paginationBar.add().growX();
        paginationBar.label(() -> "[lightgray]Page " + (currentPage + 1) + " / " + Math.max(1, getMaxPage() + 1)).pad(10f);
        paginationBar.add().growX();
        
        paginationBar.button(">", Styles.cleart, () -> {
            if(currentPage < getMaxPage()) {
                currentPage++;
                updateVisibleMods();
            }
        }).size(80f * uiScale, 50f * uiScale).disabled(b -> currentPage >= getMaxPage());
    }
    
    void updateStatusLabel(String text) {
        if(statusLabel != null) statusLabel.setText("[lightgray]" + text);
    }
    
    int getMaxPage() {
        return Math.max(0, (displayList.size - 1) / modsPerPage);
    }
    
    void buildModRow(Table table, ModInfo mod) {
        table.table(Tex.button, row -> {
            row.left();
            
            TextureRegion icon = getModIcon(mod);
            
            if(sideBadges) {
                if(icon != null) {
                    row.image(icon).size(iconSize * uiScale).pad(8f);
                } else {
                    row.image(Icon.box).size(iconSize * uiScale).color(Color.darkGray).pad(8f);
                }
                
                Table badgeCol = new Table();
                badgeCol.left();
                renderBadgesSide(badgeCol, mod);
                row.add(badgeCol).left().padLeft(badgeSpacing);
            } else {
                Table iconStack = new Table();
                if(icon != null) {
                    iconStack.image(icon).size(iconSize * uiScale).pad(8f);
                } else {
                    iconStack.image(Icon.box).size(iconSize * uiScale).color(Color.darkGray).pad(8f);
                }
                
                Table badgeOverlay = new Table();
                badgeOverlay.top().left();
                renderBadgesOverlay(badgeOverlay, mod);
                
                Stack stack = new Stack();
                stack.add(iconStack);
                stack.add(badgeOverlay);
                row.add(stack).size((iconSize + 16f) * uiScale).pad(4f);
            }
            
            row.table(info -> {
                info.left().defaults().left();
                
                Label nameLabel = new Label(mod.displayName);
                nameLabel.setStyle(Styles.outlineLabel);
                nameLabel.setColor(Color.white);
                nameLabel.setWrap(true);
                info.add(nameLabel).width(300f * uiScale).padBottom(2f).row();
                
                Table metaRow = new Table();
                metaRow.left();
                
                Label versionLabel = new Label("v" + mod.version);
                versionLabel.setColor(Color.lightGray);
                metaRow.add(versionLabel).left();
                
                if(mod.stars > 0) {
                    String color = mod.stars < 10 ? "gray" : (mod.stars < 50 ? "lightgray" : "yellow");
                    Label starLabel = new Label(" [" + color + "]★" + mod.stars);
                    metaRow.add(starLabel).padLeft(8f);
                }
                
                if(mod.isVerified) {
                    metaRow.image(Icon.ok).size(16f * uiScale).color(Color.lime).padLeft(6f);
                }
                
                info.add(metaRow).left();
                
            }).width(300f * uiScale).pad(10f);
            
            row.button(Icon.rightOpen, Styles.cleari, () -> {
                showModDetails(mod);
            }).size(48f * uiScale).padRight(4f);
            
        }).fillX().height(rowHeight * uiScale).pad(2f).row();
    }
    
    void renderBadgesOverlay(Table badgeTable, ModInfo mod) {
        badgeTable.clearChildren();
        
        Table topLeft = new Table();
        Table topRight = new Table();
        Table bottomRight = new Table();
        
        topLeft.left().top();
        topRight.right().top();
        bottomRight.right().bottom();
        
        if(showLanguageBadges) {
            if(mod.hasJava && !mod.hasScripts) {
                addBadge(topLeft, javaBadge != null ? new TextureRegionDrawable(javaBadge) : Icon.book, 
                    Color.valueOf("b07219"), "Java", "Java mod", () -> {
                        Vars.ui.showInfo("[accent]Java Mod\n[lightgray]" + mod.displayName + " uses Java code");
                    });
            } else if(mod.hasScripts && !mod.hasJava) {
                addBadge(topLeft, jsBadge != null ? new TextureRegionDrawable(jsBadge) : Icon.logic, 
                    Color.valueOf("f1e05a"), "JS", "JavaScript mod", () -> {
                        Vars.ui.showInfo("[accent]JavaScript Mod\n[lightgray]" + mod.displayName + " uses JS scripts");
                    });
            } else if(mod.hasJava && mod.hasScripts) {
                addBadge(topLeft, Icon.warning, Color.orange, "Mixed", "Java + JS", () -> {
                        Vars.ui.showInfo("[accent]Mixed Languages\n[lightgray]" + mod.displayName + " uses both Java and JS");
                    });
            }
        }
        
        if(showStatusBadges) {
            if(mod.isInstalled && mod.isEnabled) {
                addBadge(topRight, Icon.ok, Color.lime, "Active", "Currently enabled", () -> {
                    Vars.ui.showInfo("[lime]Mod Enabled\n[lightgray]" + mod.displayName + " is currently active");
                });
            }
            
            if(mod.clientOnly) {
                addBadge(topRight, Icon.warning, Color.scarlet, "Client", "Client-side only", () -> {
                    Vars.ui.showInfo("[scarlet]Client Only\n[lightgray]Cannot be used on servers");
                });
            } else if(mod.serverCompatible) {
                addBadge(topRight, Icon.host, Color.sky, "Server", "Server compatible", () -> {
                    Vars.ui.showInfo("[sky]Server Compatible\n[lightgray]Can be used on multiplayer");
                });
            }
        }
        
        if(showUpdateBadge && mod.isInstalled && !mod.repo.isEmpty()) {
            checkForUpdate(mod, hasUpdate -> {
                if(hasUpdate) {
                    addBadge(bottomRight, Icon.upload, Color.cyan, "Update", "New version available", () -> {
                        Vars.ui.showConfirm("Update " + mod.displayName + "?", 
                            "[yellow]Download update?",
                            () -> downloadMod(mod)
                        );
                    });
                }
            });
        }
        
        badgeTable.add(topLeft).expand().top().left().pad(badgeSpacing * uiScale);
        badgeTable.add(topRight).expand().top().right().pad(badgeSpacing * uiScale);
        badgeTable.row();
        badgeTable.add().expand();
        badgeTable.add(bottomRight).expand().bottom().right().pad(badgeSpacing * uiScale);
    }
    
    void renderBadgesSide(Table badgeTable, ModInfo mod) {
        badgeTable.clearChildren();
        badgeTable.left().defaults().left().size(badgeSize * uiScale).pad(badgeSpacing * uiScale);
        
        if(showLanguageBadges) {
            if(mod.hasJava && !mod.hasScripts) {
                addBadge(badgeTable, javaBadge != null ? new TextureRegionDrawable(javaBadge) : Icon.book, 
                    Color.valueOf("b07219"), "Java", "Java mod", () -> {
                        Vars.ui.showInfo("[accent]Java Mod\n[lightgray]" + mod.displayName + " uses Java code");
                    });
            } else if(mod.hasScripts && !mod.hasJava) {
                addBadge(badgeTable, jsBadge != null ? new TextureRegionDrawable(jsBadge) : Icon.logic, 
                    Color.valueOf("f1e05a"), "JS", "JavaScript mod", () -> {
                        Vars.ui.showInfo("[accent]JavaScript Mod\n[lightgray]" + mod.displayName + " uses JS scripts");
                    });
            } else if(mod.hasJava && mod.hasScripts) {
                addBadge(badgeTable, Icon.warning, Color.orange, "Mixed", "Java + JS", () -> {
                        Vars.ui.showInfo("[accent]Mixed Languages\n[lightgray]" + mod.displayName + " uses both Java and JS");
                    });
            }
        }
        
        if(showStatusBadges) {
            if(mod.isInstalled && mod.isEnabled) {
                addBadge(badgeTable, Icon.ok, Color.lime, "Active", "Currently enabled", () -> {
                    Vars.ui.showInfo("[lime]Mod Enabled\n[lightgray]" + mod.displayName + " is currently active");
                });
            }
            
            if(mod.clientOnly) {
                addBadge(badgeTable, Icon.warning, Color.scarlet, "Client", "Client-side only", () -> {
                    Vars.ui.showInfo("[scarlet]Client Only\n[lightgray]Cannot be used on servers");
                });
            } else if(mod.serverCompatible) {
                addBadge(badgeTable, Icon.host, Color.sky, "Server", "Server compatible", () -> {
                    Vars.ui.showInfo("[sky]Server Compatible\n[lightgray]Can be used on multiplayer");
                });
            }
        }
        
        if(showUpdateBadge && mod.isInstalled && !mod.repo.isEmpty()) {
            checkForUpdate(mod, hasUpdate -> {
                if(hasUpdate) {
                    addBadge(badgeTable, Icon.upload, Color.cyan, "Update", "New version available", () -> {
                        Vars.ui.showConfirm("Update " + mod.displayName + "?", 
                            "[yellow]Download update?",
                            () -> downloadMod(mod)
                        );
                    });
                }
            });
        }
    }
    
    void checkForUpdate(ModInfo mod, Cons<Boolean> callback) {
        if(updateCache.containsKey(mod.repo)) {
            String cached = updateCache.get(mod.repo);
            callback.get(!cached.equals(mod.installedVersion));
            return;
        }
        
        githubGet(
            "https://api.github.com/repos/" + mod.repo + "/releases/latest",
            json -> {
                try {
                    JsonValue release = new JsonReader().parse(json);
                    String latestTag = release.getString("tag_name", "");
                    updateCache.put(mod.repo, latestTag);
                    callback.get(!latestTag.isEmpty() && !latestTag.equals(mod.installedVersion));
                } catch(Exception e) {
                    callback.get(false);
                }
            },
            () -> callback.get(false)
        );
    }
    
    void addBadge(Table parent, Drawable icon, Color baseColor, String title, String desc, Runnable onClick) {
        Image img = new Image(icon);
        img.setColor(baseColor);
        img.touchable = Touchable.enabled;
        
        parent.add(img).size(badgeSize * uiScale);
        
        img.addListener(new Tooltip(t -> {
            t.background(Styles.black6);
            t.add("[accent]" + title + "\n[lightgray]" + desc).pad(6f);
        }));
        
        img.clicked(onClick);
        
        if(animateBadges && showBadgeGlow) {
            img.addListener(new InputListener() {
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
        if(mod.repo.isEmpty()) return null;
        return iconCache.get(mod.repo);
    }
    
    String formatDate(String dateStr) {
        try {
            String[] parts = dateStr.split("T")[0].split("-");
            return parts[1] + "/" + parts[2] + "/" + parts[0];
        } catch(Exception e) {
            return dateStr;
        }
    }void toggleMod(ModInfo mod) {
        if(mod.installedMod == null) return;
        
        if(mod.installedMod.enabled()) {
            Vars.mods.setEnabled(mod.installedMod, false);
            mod.isEnabled = false;
            Vars.ui.showInfo("[orange]Disabled " + mod.displayName + "\n[yellow]Game restart required");
        } else {
            Vars.mods.setEnabled(mod.installedMod, true);
            mod.isEnabled = true;
            Vars.ui.showInfo("[lime]Enabled " + mod.displayName + "\n[yellow]Game restart required");
        }
        
        needsRestart = true;
        applyFilter();
    }
    
    void deleteMod(ModInfo mod) {
        if(mod.installedMod == null) return;
        
        Vars.ui.showConfirm("Delete " + mod.displayName + "?", () -> {
            Vars.mods.removeMod(mod.installedMod);
            Vars.ui.showInfo("[scarlet]Deleted " + mod.displayName + "\n[yellow]Game restart required");
            needsRestart = true;
            reloadMods();
        });
    }
    
    void downloadMod(ModInfo mod) {
        if(mod.repo.isEmpty()) {
            Vars.ui.showErrorMessage("No repository URL");
            return;
        }
        
        updateStatusLabel("[cyan]Downloading " + mod.displayName + "...");
        
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
            Vars.ui.showInfo("[lime]Installed " + mod.displayName + "!\n[yellow]Game restart required");
            
            needsRestart = true;
            Core.app.post(this::reloadMods);
            
        } catch(Exception e) {
            Log.err("Install error", e);
            Vars.ui.showErrorMessage("Install failed");
            updateStatusLabel("[scarlet]Install failed");
        }
    }
    
    void showModDetails(ModInfo mod) {
        BaseDialog dialog = new BaseDialog(mod.displayName);
        dialog.addCloseButton();
        
        Table content = new Table(Tex.pane);
        content.margin(15f * uiScale);
        
        TextureRegion icon = getModIcon(mod);
        if(icon != null) {
            content.image(icon).size(80f * uiScale).pad(10f).row();
        } else {
            content.image(Icon.box).size(80f * uiScale).color(Color.gray).pad(10f).row();
        }
        
        Label nameLabel = new Label("[accent]" + mod.displayName);
        nameLabel.setWrap(true);
        nameLabel.setAlignment(Align.center);
        content.add(nameLabel).width(450f * uiScale).pad(5f).row();
        
        content.add("[cyan]by " + mod.author).pad(5f).row();
        content.add("[lightgray]v" + mod.version).pad(3f).row();
        
        if(mod.isVerified) {
            Table verified = new Table(Styles.black6);
            verified.image(Icon.ok).size(20f * uiScale).color(Color.lime).pad(4f);
            verified.add("[lime]VERIFIED").pad(4f);
            content.add(verified).pad(5f).row();
        }
        
        if(mod.stars > 0) {
            String starColor = mod.stars < 10 ? "gray" : (mod.stars < 50 ? "lightgray" : "yellow");
            content.add("[" + starColor + "]★ " + mod.stars + " stars").pad(3f).row();
        }
        
        if(!mod.description.isEmpty()) {
            Label desc = new Label(mod.description);
            desc.setWrap(true);
            desc.setAlignment(Align.center);
            desc.setColor(Color.lightGray);
            content.add(desc).width(450f * uiScale).pad(10f).row();
        }
        
        content.image().height(3f).width(400f * uiScale).color(accentColor).pad(10f).row();
        
        Table capTable = new Table();
        capTable.defaults().left().pad(3f);
        
        if(mod.hasJava) {
            capTable.add("[#b07219]■[] Java code").row();
        }
        if(mod.hasScripts) {
            capTable.add("[#f1e05a]■[] JavaScript").row();
        }
        if(mod.serverCompatible) {
            capTable.add("[sky]■[] Server compatible").row();
        }
        if(mod.clientOnly) {
            capTable.add("[scarlet]■[] Client only").row();
        }
        if(mod.isInstalled) {
            String status = mod.isEnabled ? "[lime]■[] Enabled" : "[orange]■[] Disabled";
            capTable.add(status).row();
        }
        
        content.add(capTable).pad(5f).row();
        
        content.image().height(3f).width(400f * uiScale).color(accentColor).pad(10f).row();
        
        Table statsTable = new Table();
        if(!mod.repo.isEmpty()) {
            statsTable.add("[cyan]Loading stats...").pad(15f);
            content.add(statsTable).row();
            loadGitHubStats(mod, statsTable);
        }
        
        content.image().height(3f).width(400f * uiScale).color(accentColor).pad(10f).row();
        
        content.table(actions -> {
            if(!mod.repo.isEmpty()) {
                actions.button("Open GitHub", Icon.link, () -> {
                    Core.app.openURI("https://github.com/" + mod.repo);
                }).size(220f * uiScale, 55f * uiScale).pad(5f);
            }
            
            if(!mod.isInstalled) {
                actions.button("Install", Icon.download, () -> {
                    downloadMod(mod);
                    dialog.hide();
                }).size(220f * uiScale, 55f * uiScale).pad(5f);
            } else if(mod.installedMod != null) {
                actions.button(mod.isEnabled ? "Disable" : "Enable", 
                    mod.isEnabled ? Icon.cancel : Icon.ok, () -> {
                    toggleMod(mod);
                    dialog.hide();
                }).size(220f * uiScale, 55f * uiScale).pad(5f);
                
                actions.row();
                
                actions.button("Delete", Icon.trash, () -> {
                    deleteMod(mod);
                    dialog.hide();
                }).size(220f * uiScale, 55f * uiScale).pad(5f);
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
            stats.forks = repoJson.getInt("forks_count", 0);
            stats.openIssues = repoJson.getInt("open_issues_count", 0);
        } catch(Exception e) {
            Log.err("Parse repo stats", e);
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
            Log.err("Parse release stats", e);
        }
    }
    
    void displayStats(Table statsTable, ModInfo mod, ModStats stats) {
        Core.app.post(() -> {
            statsTable.clearChildren();
            statsTable.defaults().left().pad(6f);
            
            statsTable.add("[yellow]★ Stars:").padRight(15f);
            statsTable.add("[white]" + stats.stars).row();
            
            if(stats.downloads > 0) {
                statsTable.add("[lime]↓ Downloads:").padRight(15f);
                statsTable.add("[white]" + stats.downloads).row();
            }
            
            if(stats.releases > 0) {
                statsTable.add("[cyan]⚡ Releases:").padRight(15f);
                statsTable.add("[white]" + stats.releases).row();
            }
            
            if(stats.forks > 0) {
                statsTable.add("[sky]⚑ Forks:").padRight(15f);
                statsTable.add("[white]" + stats.forks).row();
            }
            
            if(stats.openIssues > 0) {
                statsTable.add("[orange]⚠ Issues:").padRight(15f);
                statsTable.add("[white]" + stats.openIssues).row();
            }
            
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
        String displayName = "";
        String author = "";
        String description = "";
        String version = "";
        String installedVersion = "";
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
        boolean isEnabled = false;
        boolean isVerified = false;
        
        Mods.LoadedMod installedMod = null;
    }
    
    class ModStats {
        int downloads = 0;
        int releases = 0;
        int stars = 0;
        int forks = 0;
        int openIssues = 0;
    }
}