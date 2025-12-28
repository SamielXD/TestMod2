// Enhanced ModInfo for Mindustry 154 - Smooth & Fast

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
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
import java.net.*;
import java.io.*;

public class TestMod extends Mod {
    
    private Seq<ModInfo> allMods = new Seq<>();
    private BaseDialog browserDialog;
    private Table modList;
    
    public TestMod() {
        Events.on(ClientLoadEvent.class, e -> {
            // Add button inside the mods menu
            Vars.ui.mods.buttons.button("Browse All Mods", Icon.download, () -> {
                showModBrowser();
            }).size(200f, 50f);
        });
    }
    
    void showModBrowser() {
        if (browserDialog != null) {
            browserDialog.show();
            return;
        }
        
        browserDialog = new BaseDialog("Mod Browser");
        browserDialog.addCloseButton();
        
        modList = new Table();
        ScrollPane pane = new ScrollPane(modList);
        pane.setFadeScrollBars(false);
        
        browserDialog.cont.add("[accent]Loading mods...").row();
        browserDialog.cont.add(pane).grow().row();
        browserDialog.show();
        
        // Load mods in background
        fetchModListAsync();
    }
    
    void fetchModListAsync() {
        Core.app.post(() -> {
            try {
                String url = "https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ModInfo-Enhanced");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                Seq<ModInfo> mods = parseModList(response.toString());
                
                // Sort: recently updated first, then by stars
                mods.sort(m -> -parseTimestamp(m.lastUpdated));
                
                Core.app.post(() -> {
                    allMods = mods;
                    displayMods();
                });
                
            } catch (Exception ex) {
                Log.err("Failed to fetch mod list", ex);
                Core.app.post(() -> {
                    browserDialog.cont.clear();
                    browserDialog.cont.add("[scarlet]Failed to load mods").row();
                    ScrollPane pane = new ScrollPane(modList);
                    browserDialog.cont.add(pane).grow().row();
                });
            }
        });
    }
    
    void displayMods() {
        browserDialog.cont.clear();
        
        // Header
        Table header = new Table();
        header.add("[accent]Found " + allMods.size + " mods").padBottom(5);
        browserDialog.cont.add(header).growX().row();
        
        // Mod list
        ScrollPane pane = new ScrollPane(modList);
        pane.setFadeScrollBars(false);
        browserDialog.cont.add(pane).grow().row();
        
        modList.clear();
        modList.margin(10f);
        
        // Add mods one by one (smooth rendering)
        int[] index = {0};
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (index[0] < allMods.size) {
                    buildModEntry(modList, allMods.get(index[0]));
                    index[0]++;
                } else {
                    this.cancel();
                }
            }
        }, 0f, 0.02f); // Add one mod every 0.02 seconds
    }
    
    void buildModEntry(Table table, ModInfo mod) {
        table.table(Tex.button, t -> {
            t.margin(8f);
            
            // Left side - Icon placeholder
            Stack iconStack = new Stack();
            Table iconTable = new Table();
            
            Image iconImg = new Image(Icon.book);
            iconStack.add(iconImg);
            
            // Loading indicator
            if (mod.iconTexture == null && !mod.iconLoading) {
                mod.iconLoading = true;
                // Load icon in background
                loadModIconAsync(mod, iconImg);
            }
            
            t.add(iconStack).size(48f).pad(4f);
            
            // Middle - Basic Info (no stats yet)
            Table info = new Table();
            info.left();
            
            info.add("[accent]" + mod.name).left().row();
            info.add("[lightgray]by " + mod.author).left().padTop(2f).row();
            
            String desc = mod.description;
            if (desc.length() > 80) desc = desc.substring(0, 80) + "...";
            info.add("[gray]" + desc).left().width(350f).wrap().padTop(4f).row();
            
            info.add("[darkgray]Updated: " + formatDate(mod.lastUpdated))
                .left().padTop(4f).get().setFontScale(0.7f);
            
            t.add(info).growX().padLeft(10f).padRight(10f);
            
            // Right side - View button
            t.button("View", Icon.right, Styles.cleart, () -> {
                showModDetails(mod);
            }).size(80f, 50f).padRight(5f);
            
        }).growX().pad(4f).row();
    }
    
    void showModDetails(ModInfo mod) {
        BaseDialog detailDialog = new BaseDialog(mod.name);
        detailDialog.addCloseButton();
        
        Table content = new Table();
        ScrollPane pane = new ScrollPane(content);
        
        content.margin(15f);
        content.defaults().left();
        
        // Icon
        if (mod.iconTexture != null) {
            content.image(new TextureRegionDrawable(new TextureRegion(mod.iconTexture)))
                .size(64f).pad(10f).row();
        }
        
        content.add("[accent]" + mod.name).pad(5f).row();
        content.add("[lightgray]by " + mod.author).pad(3f).row();
        content.add("").height(10).row();
        
        // Description
        if (mod.description != null && !mod.description.isEmpty()) {
            content.add("[white]" + mod.description).width(450f).wrap().pad(5f).row();
            content.add("").height(10).row();
        }
        
        // Create stats table that we'll update
        Table statsTable = new Table();
        statsTable.add("[accent]Loading statistics...").pad(5f).row();
        content.add(statsTable).pad(5f).row();
        
        // Buttons
        content.add("").height(15).row();
        content.button("Open on GitHub", Icon.link, () -> {
            Core.app.openURI(mod.repo);
        }).size(200f, 50f).pad(5f).row();
        
        detailDialog.cont.add(pane).grow();
        detailDialog.show();
        
        // Fetch detailed stats in background
        fetchModStats(mod, stats -> {
            statsTable.clear();
            statsTable.add("[yellow]★ " + stats.stars + " [white]stars").pad(3f).row();
            statsTable.add("[accent]↓ " + stats.downloads + " [white]downloads").pad(3f).row();
            statsTable.add("[cyan]⚡ " + stats.releases + " [white]releases").pad(3f).row();
            statsTable.add("[lightgray]Version: " + mod.version).pad(3f).row();
            statsTable.add("[darkgray]Updated: " + formatDate(mod.lastUpdated)).pad(3f).row();
        });
    }
    
    void fetchModStats(ModInfo mod, Cons<ModStats> callback) {
        Core.app.post(() -> {
            try {
                // Extract owner/repo from URL
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;
                
                String owner = parts[0];
                String repo = parts[1];
                
                // Get releases
                String releasesUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases";
                HttpURLConnection relConn = (HttpURLConnection) new URL(releasesUrl).openConnection();
                relConn.setRequestProperty("User-Agent", "ModInfo-Enhanced");
                relConn.setConnectTimeout(5000);
                relConn.setReadTimeout(5000);
                
                BufferedReader relReader = new BufferedReader(new InputStreamReader(relConn.getInputStream()));
                StringBuilder relData = new StringBuilder();
                String line;
                while ((line = relReader.readLine()) != null) {
                    relData.append(line);
                }
                relReader.close();
                
                // Get repo info
                String repoApiUrl = "https://api.github.com/repos/" + owner + "/" + repo;
                HttpURLConnection repoConn = (HttpURLConnection) new URL(repoApiUrl).openConnection();
                repoConn.setRequestProperty("User-Agent", "ModInfo-Enhanced");
                repoConn.setConnectTimeout(5000);
                repoConn.setReadTimeout(5000);
                
                BufferedReader repoReader = new BufferedReader(new InputStreamReader(repoConn.getInputStream()));
                StringBuilder repoData = new StringBuilder();
                while ((line = repoReader.readLine()) != null) {
                    repoData.append(line);
                }
                repoReader.close();
                
                ModStats stats = new ModStats();
                stats.stars = parseStars(repoData.toString());
                stats.releases = countOccurrences(relData.toString(), "\"tag_name\"");
                stats.downloads = countDownloads(relData.toString());
                
                Core.app.post(() -> callback.get(stats));
                
            } catch (Exception e) {
                Log.err("Failed to fetch stats for " + mod.name, e);
            }
        });
    }
    
    void loadModIconAsync(ModInfo mod, Image iconImg) {
        Core.app.post(() -> {
            try {
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;
                
                String iconUrl = "https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/master/icon.png";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(iconUrl).openConnection();
                conn.setRequestProperty("User-Agent", "ModInfo-Enhanced");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    
                    byte[] data = new byte[4096];
                    int n;
                    while ((n = in.read(data)) != -1) {
                        buffer.write(data, 0, n);
                    }
                    in.close();
                    
                    byte[] imageData = buffer.toByteArray();
                    
                    Core.app.post(() -> {
                        try {
                            Pixmap pixmap = new Pixmap(imageData, 0, imageData.length);
                            mod.iconTexture = new Texture(pixmap);
                            iconImg.setDrawable(new TextureRegionDrawable(new TextureRegion(mod.iconTexture)));
                            pixmap.dispose();
                        } catch (Exception e) {}
                    });
                }
            } catch (Exception e) {}
        });
    }
    
    Seq<ModInfo> parseModList(String json) {
        Seq<ModInfo> mods = new Seq<>();
        try {
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
            
            String[] modObjects = json.split("\\},\\s*\\{");
            
            for (String modStr : modObjects) {
                modStr = modStr.trim();
                if (!modStr.startsWith("{")) modStr = "{" + modStr;
                if (!modStr.endsWith("}")) modStr = modStr + "}";
                
                ModInfo mod = new ModInfo();
                mod.repo = extractJsonValue(modStr, "repo");
                mod.name = extractJsonValue(modStr, "name");
                mod.author = extractJsonValue(modStr, "author");
                mod.description = extractJsonValue(modStr, "description");
                mod.version = extractJsonValue(modStr, "version");
                mod.lastUpdated = extractJsonValue(modStr, "lastUpdated");
                
                if (mod.repo != null && mod.name != null) {
                    mods.add(mod);
                }
            }
        } catch (Exception e) {
            Log.err("Parse error", e);
        }
        return mods;
    }
    
    String extractJsonValue(String json, String key) {
        try {
            int start = json.indexOf("\"" + key + "\"");
            if (start == -1) return "";
            start = json.indexOf(":", start) + 1;
            start = json.indexOf("\"", start) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                return json.substring(start, end)
                    .replace("\\n", " ")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            }
        } catch (Exception e) {}
        return "";
    }
    
    long parseTimestamp(String dateStr) {
        try {
            // Simple year-month-day comparison
            String[] parts = dateStr.split("-");
            if (parts.length >= 3) {
                long year = Long.parseLong(parts[0]) * 10000000000L;
                long month = Long.parseLong(parts[1]) * 100000000L;
                long day = Long.parseLong(parts[2].substring(0, 2)) * 1000000L;
                return year + month + day;
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
    
    int countOccurrences(String str, String find) {
        int count = 0, index = 0;
        while ((index = str.indexOf(find, index)) != -1) {
            count++;
            index += find.length();
        }
        return count;
    }
    
    int countDownloads(String data) {
        int total = 0;
        String search = "\"download_count\":";
        int index = 0;
        while ((index = data.indexOf(search, index)) != -1) {
            index += search.length();
            int end = Math.min(data.indexOf(",", index), data.indexOf("}", index));
            if (end == -1) end = data.indexOf("}", index);
            try {
                total += Integer.parseInt(data.substring(index, end).trim());
            } catch (Exception e) {}
        }
        return total;
    }
    
    int parseStars(String data) {
        try {
            int start = data.indexOf("\"stargazers_count\":");
            if (start == -1) return 0;
            start += 19;
            int end = Math.min(data.indexOf(",", start), data.indexOf("}", start));
            return Integer.parseInt(data.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }
    
    class ModInfo {
        String repo, name, author, description = "", version = "", lastUpdated = "";
        Texture iconTexture;
        boolean iconLoading;
    }
    
    class ModStats {
        int downloads, releases, stars;
    }
}