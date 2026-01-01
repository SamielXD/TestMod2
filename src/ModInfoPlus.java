import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.event.*;
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
import static mindustry.Vars.*;

public class ModInfoPlus extends Mod {
    
    static ModInfoCache cache;
    static IconManager iconManager;
    static BadgeSystem badgeSystem;
    static ModBrowserDialog browserDialog;
    
    public static float uiScale = 1f;
    public static float iconSize = 64f;
    public static float badgeSize = 16f;
    
    public void init() {
        cache = new ModInfoCache();
        iconManager = new IconManager();
        badgeSystem = new BadgeSystem();
        browserDialog = new ModBrowserDialog();
        
        Events.on(ClientLoadEvent.class, e -> {
            ui.mods.buttons.button("Enhanced Browser", Icon.list, () -> {
                browserDialog.show();
            }).size(200f, 64f);
        });
        
        loadSettings();
    }
    
    void loadSettings() {
        uiScale = Core.settings.getFloat("modinfo-uiscale", 1f);
        iconSize = Core.settings.getFloat("modinfo-iconsize", 64f);
        badgeSize = Core.settings.getFloat("modinfo-badgesize", 16f);
    }
    
    void saveSettings() {
        Core.settings.put("modinfo-uiscale", uiScale);
        Core.settings.put("modinfo-iconsize", iconSize);
        Core.settings.put("modinfo-badgesize", badgeSize);
    }
    
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("modinfo", "Open enhanced mod browser", (args, player) -> {
            Call.infoMessage("Use the Enhanced Browser button in the mods menu");
        });
    }
}

class ModInfo {
    String name;
    String displayName;
    String author;
    String description;
    String version;
    String repo;
    int stars;
    long lastUpdated;
    boolean isInstalled;
    boolean isEnabled;
    boolean hasJava;
    boolean hasJS;
    boolean hasContent;
    boolean serverCompatible;
    boolean hasUpdate;
    TextureRegion icon;
    Mods.LoadedMod loadedMod;
    
    ModInfo(Mods.LoadedMod mod) {
        this.loadedMod = mod;
        this.name = mod.name;
        this.displayName = mod.meta.displayName != null ? mod.meta.displayName : mod.name;
        this.author = mod.meta.author;
        this.description = mod.meta.description;
        this.version = mod.meta.version;
        this.repo = mod.meta.repo;
        this.isInstalled = true;
        this.isEnabled = mod.enabled();
        
        detectModType(mod);
    }
    
    ModInfo(String name, String displayName, String author, String desc, String version, String repo, int stars) {
        this.name = name;
        this.displayName = displayName != null ? displayName : name;
        this.author = author;
        this.description = desc;
        this.version = version;
        this.repo = repo;
        this.stars = stars;
        this.isInstalled = false;
        this.isEnabled = false;
    }
    
    void detectModType(Mods.LoadedMod mod) {
        Fi root = mod.root;
        hasJava = root.child("classes").exists();
        hasJS = root.child("scripts").exists();
        hasContent = root.child("content").exists();
        serverCompatible = !mod.meta.hidden;
    }
}

class ModInfoCache {
    ObjectMap<String, ModInfo> installedMods = new ObjectMap<>();
    ObjectMap<String, ModInfo> remoteMods = new ObjectMap<>();
    ObjectMap<String, Boolean> updateCache = new ObjectMap<>();
    boolean remoteLoaded = false;
    
    ModInfoCache() {
        refreshInstalled();
    }
    
    void refreshInstalled() {
        installedMods.clear();
        for(Mods.LoadedMod mod : mods.list()) {
            ModInfo info = new ModInfo(mod);
            installedMods.put(mod.name, info);
        }
    }
    
    void loadRemoteMods() {
        if(remoteLoaded) return;
        
        Http.get("https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json", response -> {
            try {
                Jval json = Jval.read(response.getResultAsString());
                for(Jval modData : json.asArray()) {
                    String name = modData.getString("name", "");
                    String displayName = modData.getString("displayName", name);
                    String author = modData.getString("author", "");
                    String desc = modData.getString("description", "");
                    String version = modData.getString("version", "");
                    String repo = modData.getString("repo", "");
                    int stars = modData.getInt("stars", 0);
                    
                    ModInfo info = new ModInfo(name, displayName, author, desc, version, repo, stars);
                    remoteMods.put(name, info);
                }
                remoteLoaded = true;
            } catch(Exception e) {
                Log.err("Failed to load remote mods", e);
            }
        }, error -> {
            Log.err("HTTP error loading mods", error);
        });
    }
    
    boolean hasUpdate(ModInfo mod) {
        if(!mod.isInstalled || mod.version == null) return false;
        
        String key = mod.name + ":" + mod.version;
        if(updateCache.containsKey(key)) {
            return updateCache.get(key);
        }
        
        ModInfo remote = remoteMods.get(mod.name);
        if(remote != null && remote.version != null) {
            boolean hasUpdate = compareVersions(mod.version, remote.version) < 0;
            updateCache.put(key, hasUpdate);
            return hasUpdate;
        }
        
        return false;
    }
    
    int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.replaceAll("[^0-9.]", "").split("\\.");
            String[] parts2 = v2.replaceAll("[^0-9.]", "").split("\\.");
            int len = Math.max(parts1.length, parts2.length);
            
            for(int i = 0; i < len; i++) {
                int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                if(p1 < p2) return -1;
                if(p1 > p2) return 1;
            }
            return 0;
        } catch(Exception e) {
            return 0;
        }
    }
}

class IconManager {
    ObjectMap<String, TextureRegion> iconCache = new ObjectMap<>();
    TextureRegion defaultIcon;
    
    IconManager() {
        defaultIcon = Core.atlas.find("icon-missing");
    }
    
    TextureRegion getIcon(ModInfo mod) {
        if(iconCache.containsKey(mod.name)) {
            return iconCache.get(mod.name);
        }
        
        if(mod.isInstalled && mod.loadedMod != null) {
            Fi iconFile = mod.loadedMod.root.child("icon.png");
            if(iconFile.exists()) {
                try {
                    Texture tex = new Texture(iconFile);
                    TextureRegion region = new TextureRegion(tex);
                    iconCache.put(mod.name, region);
                    return region;
                } catch(Exception e) {
                    Log.err("Failed to load icon for " + mod.name, e);
                }
            }
        }
        
        if(mod.repo != null && !mod.repo.isEmpty()) {
            loadRemoteIcon(mod);
        }
        
        return defaultIcon;
    }
    
    void loadRemoteIcon(ModInfo mod) {
        String iconUrl = mod.repo.replace("github.com", "raw.githubusercontent.com") + "/master/icon.png";
        
        Http.get(iconUrl, response -> {
            try {
                byte[] data = response.getResult();
                Pixmap pixmap = new Pixmap(data, 0, data.length);
                Texture tex = new Texture(pixmap);
                TextureRegion region = new TextureRegion(tex);
                iconCache.put(mod.name, region);
                pixmap.dispose();
            } catch(Exception e) {
                iconCache.put(mod.name, defaultIcon);
            }
        }, error -> {
            iconCache.put(mod.name, defaultIcon);
        });
    }
}class BadgeSystem {
    
    static class Badge {
        String text;
        Color color;
        String tooltip;
        
        Badge(String text, Color color, String tooltip) {
            this.text = text;
            this.color = color;
            this.tooltip = tooltip;
        }
    }
    
    Seq<Badge> getBadges(ModInfo mod) {
        Seq<Badge> badges = new Seq<>();
        
        if(mod.hasJava) {
            badges.add(new Badge("JAVA", Color.orange, "Contains compiled Java code"));
        }
        
        if(mod.hasJS) {
            badges.add(new Badge("JS", Color.yellow, "Contains JavaScript scripts"));
        }
        
        if(mod.hasContent) {
            badges.add(new Badge("CONTENT", Color.sky, "Adds new content to the game"));
        }
        
        if(mod.serverCompatible) {
            badges.add(new Badge("SERVER", Color.green, "Compatible with servers"));
        }
        
        if(mod.hasUpdate) {
            badges.add(new Badge("UPDATE", Color.coral, "Update available"));
        }
        
        if(mod.lastUpdated > 0) {
            long daysSince = (System.currentTimeMillis() - mod.lastUpdated) / (1000 * 60 * 60 * 24);
            if(daysSince < 30) {
                badges.add(new Badge("NEW", Color.lime, "Recently updated"));
            }
        }
        
        return badges;
    }
    
    void buildBadges(Table container, ModInfo mod) {
        Seq<Badge> badges = getBadges(mod);
        
        for(Badge badge : badges) {
            Label badgeLabel = new Label(badge.text);
            badgeLabel.setStyle(new Label.LabelStyle(badgeLabel.getStyle()));
            badgeLabel.getStyle().fontColor = badge.color;
            badgeLabel.setFontScale(0.7f);
            
            Table badgeTable = new Table();
            badgeTable.setBackground(Tex.button);
            badgeTable.add(badgeLabel).pad(2f);
            
            badgeTable.addListener(new ClickListener() {
                public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                    badgeTable.setBackground(Tex.buttonOver);
                    badgeTable.getColor().set(badge.color).a(0.8f);
                }
                
                public void exit(InputEvent event, float x, float y, int pointer, Element toActor) {
                    badgeTable.setBackground(Tex.button);
                    badgeTable.getColor().set(Color.white);
                }
            });
            
            badgeTable.addListener(new Tooltip(t -> {
                t.background(Tex.button);
                t.add(badge.tooltip).pad(4f);
            }));
            
            container.add(badgeTable).padRight(4f).height(ModInfoPlus.badgeSize);
        }
    }
}

class ModBrowserDialog extends BaseDialog {
    
    Table installedTable, enabledTable, onlineTable;
    int currentTab = 0;
    
    ModBrowserDialog() {
        super("Enhanced Mod Browser");
        
        addCloseButton();
        
        cont.table(tabs -> {
            tabs.defaults().size(200f, 50f);
            
            tabs.button("Installed", () -> {
                switchTab(0);
            }).update(b -> b.setChecked(currentTab == 0));
            
            tabs.button("Enabled/Disabled", () -> {
                switchTab(1);
            }).update(b -> b.setChecked(currentTab == 1));
            
            tabs.button("Browse Online", () -> {
                switchTab(2);
            }).update(b -> b.setChecked(currentTab == 2));
        }).growX().row();
        
        cont.pane(content -> {
            installedTable = new Table();
            enabledTable = new Table();
            onlineTable = new Table();
            
            content.add(installedTable).grow();
        }).grow().row();
        
        cont.table(footer -> {
            footer.button("Settings", Icon.settings, () -> {
                showSettings();
            }).size(150f, 50f);
            
            footer.button("Refresh", Icon.refresh, () -> {
                refresh();
            }).size(150f, 50f);
        }).growX();
        
        shown(this::refresh);
    }
    
    void switchTab(int tab) {
        currentTab = tab;
        refresh();
    }
    
    void refresh() {
        ModInfoPlus.cache.refreshInstalled();
        
        installedTable.clear();
        enabledTable.clear();
        onlineTable.clear();
        
        if(currentTab == 0) {
            buildInstalledTab();
        } else if(currentTab == 1) {
            buildEnabledTab();
        } else if(currentTab == 2) {
            buildOnlineTab();
        }
    }
    
    void buildInstalledTab() {
        installedTable.defaults().growX().pad(4f);
        
        for(ModInfo mod : ModInfoPlus.cache.installedMods.values()) {
            buildModRow(installedTable, mod);
            installedTable.row();
        }
    }
    
    void buildEnabledTab() {
        enabledTable.add("Enabled Mods").color(Color.green).colspan(2).row();
        
        for(ModInfo mod : ModInfoPlus.cache.installedMods.values()) {
            if(mod.isEnabled) {
                buildModRow(enabledTable, mod);
                enabledTable.row();
            }
        }
        
        enabledTable.row();
        enabledTable.add("Disabled Mods").color(Color.gray).colspan(2).row();
        
        for(ModInfo mod : ModInfoPlus.cache.installedMods.values()) {
            if(!mod.isEnabled) {
                buildModRow(enabledTable, mod);
                enabledTable.row();
            }
        }
    }
    
    void buildOnlineTab() {
        if(!ModInfoPlus.cache.remoteLoaded) {
            onlineTable.add("Loading online mods...").row();
            ModInfoPlus.cache.loadRemoteMods();
            Time.runTask(60f, this::refresh);
            return;
        }
        
        onlineTable.defaults().growX().pad(4f);
        
        Seq<ModInfo> sortedMods = ModInfoPlus.cache.remoteMods.values().toSeq();
        sortedMods.sort(m -> -m.stars);
        
        for(ModInfo mod : sortedMods) {
            buildModRow(onlineTable, mod);
            onlineTable.row();
        }
    }
    
    void buildModRow(Table parent, ModInfo mod) {
        mod.hasUpdate = ModInfoPlus.cache.hasUpdate(mod);
        
        Table row = new Table(Tex.button);
        row.margin(8f);
        
        TextureRegion icon = ModInfoPlus.iconManager.getIcon(mod);
        row.image(icon).size(ModInfoPlus.iconSize).pad(8f);
        
        row.table(info -> {
            info.left();
            
            Label nameLabel = info.add(mod.displayName).color(Color.white).wrap().growX().get();
            nameLabel.setAlignment(Align.left);
            info.row();
            
            if(mod.author != null && !mod.author.isEmpty()) {
                info.add("by " + mod.author).color(Color.lightGray).left().row();
            }
            
            if(mod.description != null && !mod.description.isEmpty()) {
                Label descLabel = info.add(mod.description).color(Color.gray).wrap().width(400f).left().get();
                descLabel.setAlignment(Align.left);
                info.row();
            }
            
            info.table(badges -> {
                badges.left();
                ModInfoPlus.badgeSystem.buildBadges(badges, mod);
            }).left().row();
            
        }).growX().pad(8f);
        
        row.table(stats -> {
            stats.right();
            
            if(mod.version != null) {
                stats.add("v" + mod.version).color(Color.lightGray).row();
            }
            
            stats.image(Icon.star.getRegion()).size(16f).padRight(4f);
            Color starColor = mod.stars > 10 ? Color.yellow : Color.gray;
            stats.add(String.valueOf(mod.stars)).color(starColor).row();
            
            if(mod.isInstalled) {
                stats.add(mod.isEnabled ? "[green]Enabled" : "[gray]Disabled").row();
            }
            
        }).padRight(8f);
        
        row.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                showModDetails(mod);
            }
        });
        
        parent.add(row).growX().height(ModInfoPlus.iconSize + 24f);
    }
    
    void showModDetails(ModInfo mod) {
        BaseDialog dialog = new BaseDialog(mod.displayName);
        dialog.cont.add(mod.description != null ? mod.description : "No description").width(500f).wrap().row();
        dialog.cont.add("Author: " + (mod.author != null ? mod.author : "Unknown")).row();
        dialog.cont.add("Version: " + (mod.version != null ? mod.version : "Unknown")).row();
        
        if(mod.repo != null) {
            dialog.cont.add("Repository: " + mod.repo).row();
        }
        
        dialog.addCloseButton();
        dialog.show();
    }
    
    void showSettings() {
        BaseDialog settings = new BaseDialog("Mod Browser Settings");
        
        settings.cont.add("UI Scale").padRight(10f);
        settings.cont.slider(0.5f, 2f, 0.1f, ModInfoPlus.uiScale, v -> {
            ModInfoPlus.uiScale = v;
        }).width(300f).row();
        
        settings.cont.add("Icon Size").padRight(10f);
        settings.cont.slider(32f, 128f, 8f, ModInfoPlus.iconSize, v -> {
            ModInfoPlus.iconSize = v;
        }).width(300f).row();
        
        settings.cont.add("Badge Size").padRight(10f);
        settings.cont.slider(12f, 32f, 2f, ModInfoPlus.badgeSize, v -> {
            ModInfoPlus.badgeSize = v;
        }).width(300f).row();
        
        settings.cont.add("Changes require game restart").color(Color.coral).row();
        
        settings.buttons.button("Save", () -> {
            ModInfoPlus mod = (ModInfoPlus) mods.getMod(ModInfoPlus.class);
            mod.saveSettings();
            settings.hide();
        });
        
        settings.addCloseButton();
        settings.show();
    }
}class ModRowBuilder {
    
    static Table buildCompactRow(ModInfo mod) {
        Table row = new Table(Tex.button);
        row.margin(4f);
        
        TextureRegion icon = ModInfoPlus.iconManager.getIcon(mod);
        Image iconImage = new Image(icon);
        row.add(iconImage).size(48f).pad(4f);
        
        row.table(content -> {
            content.left();
            content.add(mod.displayName).color(Color.white).left().row();
            
            content.table(meta -> {
                meta.left();
                
                if(mod.author != null && !mod.author.isEmpty()) {
                    meta.add(mod.author).color(Color.lightGray).padRight(8f);
                }
                
                if(mod.version != null) {
                    meta.add("v" + mod.version).color(Color.gray).padRight(8f);
                }
                
                meta.image(Icon.star.getRegion()).size(12f).padRight(2f);
                meta.add(String.valueOf(mod.stars)).color(mod.stars > 10 ? Color.yellow : Color.gray);
                
            }).left();
            
        }).growX().pad(4f);
        
        return row;
    }
    
    static void addModActions(Table parent, ModInfo mod) {
        if(mod.isInstalled) {
            parent.button(mod.isEnabled ? "Disable" : "Enable", () -> {
                toggleMod(mod);
            }).size(80f, 40f);
            
            parent.button("Remove", Icon.trash, () -> {
                confirmRemove(mod);
            }).size(80f, 40f);
        } else {
            parent.button("Install", Icon.download, () -> {
                installMod(mod);
            }).size(80f, 40f);
        }
    }
    
    static void toggleMod(ModInfo mod) {
        if(mod.loadedMod != null) {
            mods.setEnabled(mod.loadedMod, !mod.isEnabled);
            mod.isEnabled = mod.loadedMod.enabled();
        }
    }
    
    static void confirmRemove(ModInfo mod) {
        ui.showConfirm("Remove Mod", "Remove " + mod.displayName + "?", () -> {
            if(mod.loadedMod != null) {
                mods.removeMod(mod.loadedMod);
                ModInfoPlus.cache.refreshInstalled();
            }
        });
    }
    
    static void installMod(ModInfo mod) {
        if(mod.repo == null || mod.repo.isEmpty()) {
            ui.showInfo("Cannot install: No repository URL");
            return;
        }
        
        ui.showInfo("Installing " + mod.displayName + "...");
        
        String downloadUrl = mod.repo + "/archive/master.zip";
        
        Http.get(downloadUrl, response -> {
            try {
                byte[] data = response.getResult();
                Fi tempZip = Fi.get("mods/temp_" + mod.name + ".zip");
                tempZip.writeBytes(data);
                
                mods.importMod(tempZip);
                tempZip.delete();
                
                ModInfoPlus.cache.refreshInstalled();
                ui.showInfo(mod.displayName + " installed successfully!");
                
            } catch(Exception e) {
                ui.showException("Installation failed", e);
            }
        }, error -> {
            ui.showException("Download failed", error);
        });
    }
}

class ModFilter {
    String searchQuery = "";
    boolean showJava = true;
    boolean showJS = true;
    boolean showContent = true;
    boolean showEnabled = true;
    boolean showDisabled = true;
    
    boolean matches(ModInfo mod) {
        if(!searchQuery.isEmpty()) {
            String query = searchQuery.toLowerCase();
            boolean nameMatch = mod.displayName.toLowerCase().contains(query);
            boolean authorMatch = mod.author != null && mod.author.toLowerCase().contains(query);
            boolean descMatch = mod.description != null && mod.description.toLowerCase().contains(query);
            
            if(!nameMatch && !authorMatch && !descMatch) {
                return false;
            }
        }
        
        if(!showJava && mod.hasJava) return false;
        if(!showJS && mod.hasJS) return false;
        if(!showContent && mod.hasContent) return false;
        if(!showEnabled && mod.isEnabled) return false;
        if(!showDisabled && !mod.isEnabled) return false;
        
        return true;
    }
    
    void buildFilterUI(Table parent, Runnable onChange) {
        parent.table(search -> {
            search.field(searchQuery, text -> {
                searchQuery = text;
                onChange.run();
            }).growX().get();
            
            search.button(Icon.filter, () -> {
                showFilterDialog(onChange);
            }).size(40f);
        }).growX().row();
    }
    
    void showFilterDialog(Runnable onChange) {
        BaseDialog dialog = new BaseDialog("Filter Mods");
        
        dialog.cont.check("Show Java Mods", showJava, v -> {
            showJava = v;
            onChange.run();
        }).left().row();
        
        dialog.cont.check("Show JS Mods", showJS, v -> {
            showJS = v;
            onChange.run();
        }).left().row();
        
        dialog.cont.check("Show Content Mods", showContent, v -> {
            showContent = v;
            onChange.run();
        }).left().row();
        
        dialog.cont.check("Show Enabled", showEnabled, v -> {
            showEnabled = v;
            onChange.run();
        }).left().row();
        
        dialog.cont.check("Show Disabled", showDisabled, v -> {
            showDisabled = v;
            onChange.run();
        }).left().row();
        
        dialog.addCloseButton();
        dialog.show();
    }
}

class ModSorter {
    
    enum SortMode {
        NAME,
        AUTHOR,
        STARS,
        RECENT,
        ENABLED
    }
    
    SortMode currentMode = SortMode.STARS;
    boolean ascending = false;
    
    void sort(Seq<ModInfo> mods) {
        switch(currentMode) {
            case NAME:
                mods.sort(m -> m.displayName);
                break;
            case AUTHOR:
                mods.sort(m -> m.author != null ? m.author : "");
                break;
            case STARS:
                mods.sort(m -> -m.stars);
                break;
            case RECENT:
                mods.sort(m -> -m.lastUpdated);
                break;
            case ENABLED:
                mods.sort(m -> m.isEnabled ? 0 : 1);
                break;
        }
        
        if(ascending) {
            mods.reverse();
        }
    }
    
    void buildSortUI(Table parent, Runnable onChange) {
        parent.table(sort -> {
            sort.button("Sort: " + currentMode.name(), () -> {
                cycleSortMode();
                onChange.run();
            }).growX();
            
            sort.button(ascending ? Icon.upOpen : Icon.downOpen, () -> {
                ascending = !ascending;
                onChange.run();
            }).size(40f);
        }).growX();
    }
    
    void cycleSortMode() {
        SortMode[] modes = SortMode.values();
        int next = (currentMode.ordinal() + 1) % modes.length;
        currentMode = modes[next];
    }
}

class UpdateChecker {
    
    static void checkAllUpdates() {
        for(ModInfo mod : ModInfoPlus.cache.installedMods.values()) {
            checkUpdate(mod);
        }
    }
    
    static void checkUpdate(ModInfo mod) {
        if(!mod.isInstalled || mod.repo == null) return;
        
        String apiUrl = mod.repo.replace("github.com", "api.github.com/repos") + "/releases/latest";
        
        Http.get(apiUrl, response -> {
            try {
                Jval json = Jval.read(response.getResultAsString());
                String latestVersion = json.getString("tag_name", "").replace("v", "");
                
                if(!latestVersion.isEmpty() && mod.version != null) {
                    boolean hasUpdate = ModInfoPlus.cache.compareVersions(mod.version, latestVersion) < 0;
                    mod.hasUpdate = hasUpdate;
                    ModInfoPlus.cache.updateCache.put(mod.name + ":" + mod.version, hasUpdate);
                }
            } catch(Exception e) {
                Log.err("Failed to check update for " + mod.name, e);
            }
        }, error -> {
            Log.err("HTTP error checking update for " + mod.name, error);
        });
    }
}

class ModExporter {
    
    static void exportModList() {
        StringBuilder sb = new StringBuilder();
        sb.append("Installed Mods:\n\n");
        
        for(ModInfo mod : ModInfoPlus.cache.installedMods.values()) {
            sb.append("- ").append(mod.displayName);
            sb.append(" (").append(mod.version != null ? mod.version : "unknown").append(")");
            sb.append(" by ").append(mod.author != null ? mod.author : "unknown");
            sb.append("\n");
        }
        
        Core.app.setClipboardText(sb.toString());
        ui.showInfo("Mod list copied to clipboard");
    }
    
    static void exportEnabledMods() {
        StringBuilder sb = new StringBuilder();
        
        for(ModInfo mod : ModInfoPlus.cache.installedMods.values()) {
            if(mod.isEnabled && mod.repo != null) {
                sb.append(mod.repo).append("\n");
            }
        }
        
        Core.app.setClipboardText(sb.toString());
        ui.showInfo("Enabled mod repos copied to clipboard");
    }
}