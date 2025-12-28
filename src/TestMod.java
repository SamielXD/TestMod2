// ModInfo+ for Mindustry 154 - Ultra Smooth with Pagination

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
    private int currentPage = 0;
    private int modsPerPage = 12; // Show only 12 mods per page for smoothness
    private Label pageLabel;
    private Table navButtons;
    
    public TestMod() {
        Events.on(ClientLoadEvent.class, e -> {
            // Add button inside the mods menu
            Vars.ui.mods.buttons.button("ModInfo+", Icon.download, () -> {
                showModBrowser();
            }).size(200f, 50f);
        });
    }
    
    void showModBrowser() {
        if (browserDialog != null) {
            browserDialog.show();
            return;
        }
        
        browserDialog = new BaseDialog("ModInfo+ Browser");
        browserDialog.addCloseButton();
        
        modList = new Table();
        ScrollPane pane = new ScrollPane(modList);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        
        // Header with page info
        Table header = new Table();
        pageLabel = new Label("[accent]Loading mods...");
        header.add(pageLabel).growX().center();
        
        browserDialog.cont.add(header).growX().pad(10f).row();
        browserDialog.cont.add(pane).grow().row();
        
        // Navigation buttons
        navButtons = new Table();
        navButtons.button("◄ Previous", () -> {
            if (currentPage > 0) {
                currentPage--;
                displayCurrentPage();
            }
        }).width(150f).padRight(10f).disabled(b -> currentPage == 0);
        
        navButtons.button("Next ►", () -> {
            int maxPage = (allMods.size - 1) / modsPerPage;
            if (currentPage < maxPage) {
                currentPage++;
                displayCurrentPage();
            }
        }).width(150f).disabled(b -> currentPage >= (allMods.size - 1) / modsPerPage);
        
        browserDialog.cont.add(navButtons).pad(10f).row();
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
                conn.setRequestProperty("User-Agent", "ModInfo-Plus");
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
                
                // Sort: recently updated first
                mods.sort(m -> -parseTimestamp(m.lastUpdated));
                
                Core.app.post(() -> {
                    allMods = mods;
                    currentPage = 0;
                    displayCurrentPage();
                });
                
            } catch (Exception ex) {
                Log.err("Failed to fetch mod list", ex);
                Core.app.post(() -> {
                    pageLabel.setText("[scarlet]Failed to load mods");
                });
            }
        });
    }
    
    void displayCurrentPage() {
        modList.clear();
        modList.margin(5f);
        
        int start = currentPage * modsPerPage;
        int end = Math.min(start + modsPerPage, allMods.size);
        int maxPage = (allMods.size - 1) / modsPerPage;
        
        pageLabel.setText("[accent]" + allMods.size + " mods | Page " + (currentPage + 1) + "/" + (maxPage + 1));
        
        // Render ONLY the current page (fast!)
        for (int i = start; i < end; i++) {
            ModInfo mod = allMods.get(i);
            buildSimpleModEntry(modList, mod);
        }
        
        // Update nav button states
        navButtons.getCells().get(0).get().setDisabled(currentPage == 0);
        navButtons.getCells().get(1).get().setDisabled(currentPage >= maxPage);
    }
    
    void buildSimpleModEntry(Table table, ModInfo mod) {
        // Simplified entry - much faster to render
        table.button(b -> {
            b.left();
            b.margin(6f);
            
            // Icon (40x40 - smaller = faster)
            Image iconImg = new Image(Icon.book);
            iconImg.setScaling(Scaling.fit);
            b.add(iconImg).size(40f).padRight(10f);
            
            // Load icon async if not loaded (and only for current page)
            if (mod.iconTexture == null && !mod.iconLoading) {
                mod.iconLoading = true;
                loadModIconAsync(mod, iconImg);
            } else if (mod.iconTexture != null) {
                iconImg.setDrawable(new TextureRegionDrawable(new TextureRegion(mod.iconTexture)));
            }
            
            // Info section - simplified
            Table info = new Table();
            info.left();
            info.defaults().left();
            
            // Title only
            info.add("[accent]" + mod.name).row();
            
            // Author + Date on same line
            String dateStr = formatDate(mod.lastUpdated);
            info.add("[lightgray]" + mod.author + " [darkgray]• " + dateStr)
                .padTop(2f).get().setFontScale(0.8f);
            
            b.add(info).growX().padRight(5f);
            
        }, () -> showModDetails(mod)).growX().height(60f).pad(2f).row();
    }
    
    void showModDetails(ModInfo mod) {
        BaseDialog detailDialog = new BaseDialog(mod.name);
        detailDialog.addCloseButton();
        
        Table content = new Table();
        content.margin(15f);
        content.defaults().left().padBottom(8f);
        
        // Icon
        if (mod.iconTexture != null) {
            content.image(new TextureRegionDrawable(new TextureRegion(mod.iconTexture)))
                .size(64f).center().row();
        } else {
            content.image(Icon.book).size(64f).center().row();
        }
        
        content.add("[accent]" + mod.name).center().row();
        content.add("[lightgray]by " + mod.author).center().padBottom(15f).row();
        
        // Description
        if (mod.description != null && !mod.description.isEmpty()) {
            content.add("[white]" + mod.description).width(450f).wrap().row();
        }
        
        content.add("").height(10).row();
        
        // Stats loading
        Table statsTable = new Table();
        statsTable.defaults().left().padBottom(5f);
        statsTable.add("[accent]Loading statistics...").row();
        content.add(statsTable).row();
        
        content.add("").height(10).row();
        
        // GitHub button
        content.button("Open on GitHub", Icon.link, () -> {
            Core.app.openURI(mod.repo);
        }).size(200f, 50f);
        
        ScrollPane detailPane = new ScrollPane(content);
        detailDialog.cont.add(detailPane).grow();
        detailDialog.show();
        
        // Fetch stats async
        fetchModStats(mod, stats -> {
            statsTable.clear();
            statsTable.add("[yellow]★ " + stats.stars + " [white]stars").row();
            statsTable.add("[accent]↓ " + stats.downloads + " [white]downloads").row();
            statsTable.add("[cyan]⚡ " + stats.releases + " [white]releases").row();
            statsTable.add("[lightgray]Version: " + mod.version).row();
            statsTable.add("[darkgray]Updated: " + formatDate(mod.lastUpdated)).row();
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
                
                // Get releases
                String releasesUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases";
                HttpURLConnection relConn = (HttpURLConnection) new URL(releasesUrl).openConnection();
                relConn.setRequestProperty("User-Agent", "ModInfo-Plus");
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
                repoConn.setRequestProperty("User-Agent", "ModInfo-Plus");
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
                Log.err("Failed to fetch stats", e);
            }
        });
    }
    
    void loadModIconAsync(ModInfo mod, Image iconImg) {
        // Only load if really needed
        Core.app.post(() -> {
            try {
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;
                
                String iconUrl = "https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/master/icon.png";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(iconUrl).openConnection();
                conn.setRequestProperty("User-Agent", "ModInfo-Plus");
                conn.setConnectTimeout(2000); // Shorter timeout
                conn.setReadTimeout(2000);
                
                if (conn.getResponseCode() == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    
                    byte[] data = new byte[2048]; // Smaller buffer
                    int n;
                    while ((n = in.read(data)) != -1) {
                        buffer.write(data, 0, n);
                    }
                    in.close();
                    
                    byte[] imageData = buffer.toByteArray();
                    
                    // Only update if icon is still needed
                    Core.app.post(() -> {
                        try {
                            Pixmap pixmap = new Pixmap(imageData, 0, imageData.length);
                            mod.iconTexture = new Texture(pixmap);
                            iconImg.setDrawable(new TextureRegionDrawable(new TextureRegion(mod.iconTexture)));
                            pixmap.dispose();
                        } catch (Exception e) {
                            mod.iconLoading = false; // Reset on error
                        }
                    });
                }
            } catch (Exception e) {
                mod.iconLoading = false; // Reset on error
            }
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
            int end = data.indexOf(",", index);
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
            int end = data.indexOf(",", start);
            if (end == -1) end = data.indexOf("}", start);
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