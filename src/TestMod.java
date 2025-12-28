
// ModInfo+ Enhanced Browser v1.0 - COMPLETE CODE

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
import java.net.*;
import java.io.*;

public class TestMod extends Mod {

    private Seq<ModInfo> allMods = new Seq<>();
    private BaseDialog browserDialog;
    private Table modList;
    private int currentPage = 0;
    private int modsPerPage = 6;
    private Label pageLabel;
    private TextField searchField;
    private String searchQuery = "";
    
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;

    public TestMod() {
        Log.info("[cyan]ModInfo+ Enhanced:[] Initializing...");
    }

    @Override
    public void init() {
        loadBadgeTextures();
        
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                replaceModBrowser();
            });
        });
    }
    
    void loadBadgeTextures() {
        try {
            String[] javaNames = {"testmod-java", "java", "Java"};
            String[] jsNames = {"testmod-js", "js", "Js"};
            
            TextureRegion errorRegion = Core.atlas.find("error");
            
            for (String name : javaNames) {
                TextureRegion region = Core.atlas.find(name);
                if (region != errorRegion && region.texture != null) {
                    javaBadge = region;
                    Log.info("[lime]Java badge found: " + name);
                    break;
                }
            }
            
            for (String name : jsNames) {
                TextureRegion region = Core.atlas.find(name);
                if (region != errorRegion && region.texture != null) {
                    jsBadge = region;
                    Log.info("[lime]JS badge found: " + name);
                    break;
                }
            }
        } catch (Exception e) {
            Log.err("Failed to load badges", e);
        }
    }
    
    void replaceModBrowser() {
        try {
            BaseDialog modsDialog = Vars.ui.mods;
            Element[] found = {null};
            
            modsDialog.buttons.getChildren().each(element -> {
                if (element instanceof TextButton) {
                    TextButton btn = (TextButton) element;
                    if (btn.getText().toString().toLowerCase().contains("browser")) {
                        found[0] = btn;
                    }
                }
            });
            
            if (found[0] != null) {
                TextButton browserButton = (TextButton) found[0];
                browserButton.clearListeners();
                browserButton.clicked(() -> showEnhancedModBrowser());
                Log.info("[lime]Mod Browser REPLACED!");
            }
        } catch (Exception e) {
            Log.err("Failed to replace browser", e);
        }
    }

    void showEnhancedModBrowser() {
        if (browserDialog != null) {
            browserDialog.show();
            return;
        }

        browserDialog = new BaseDialog("@mods.browser");
        browserDialog.addCloseButton();
        browserDialog.cont.clear();

        Table mainTable = new Table();
        mainTable.background(Tex.button);
        
        Table header = new Table(Tex.underline);
        header.add("[accent]━━━[] [cyan]ModInfo+ Enhanced[] [accent]━━━[]").pad(10f).row();
        mainTable.add(header).growX().row();

        Table searchBar = new Table(Tex.buttonEdge3);
        searchBar.image(Icon.zoom).size(20f).pad(5f);
        searchField = new TextField();
        searchField.setMessageText("Search...");
        searchField.changed(() -> {
            searchQuery = searchField.getText().toLowerCase();
            currentPage = 0;
            displayCurrentPage();
        });
        searchBar.add(searchField).growX().pad(5f);
        searchBar.button(Icon.cancel, () -> {
            searchField.setText("");
            searchQuery = "";
            currentPage = 0;
            displayCurrentPage();
        }).size(40f);
        mainTable.add(searchBar).growX().pad(5f).row();

        pageLabel = new Label("[cyan]Loading...[]");
        mainTable.add(pageLabel).growX().pad(5f).row();

        modList = new Table();
        ScrollPane pane = new ScrollPane(modList);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabled(true, false);
        mainTable.add(pane).grow().row();

        Table navBar = new Table(Tex.buttonEdge2);
        navBar.button("◄ Prev", () -> {
            if (currentPage > 0) {
                currentPage--;
                displayCurrentPage();
            }
        }).width(100f).disabled(b -> currentPage == 0);
        
        navBar.add().growX();
        
        navBar.button("Next ►", () -> {
            Seq<ModInfo> filtered = getFilteredMods();
            int maxPage = Math.max(0, (filtered.size - 1) / modsPerPage);
            if (currentPage < maxPage) {
                currentPage++;
                displayCurrentPage();
            }
        }).width(100f).disabled(b -> {
            Seq<ModInfo> filtered = getFilteredMods();
            int maxPage = Math.max(0, (filtered.size - 1) / modsPerPage);
            return currentPage >= maxPage;
        });

        mainTable.add(navBar).growX().pad(5f).row();
        
        browserDialog.cont.add(mainTable).grow();
        browserDialog.show();

        fetchModListAsync();
    }
    
    Seq<ModInfo> getFilteredMods() {
        if (searchQuery.isEmpty()) return allMods;
        
        Seq<ModInfo> filtered = new Seq<>();
        for (ModInfo mod : allMods) {
            if (mod.name.toLowerCase().contains(searchQuery) || 
                mod.author.toLowerCase().contains(searchQuery)) {
                filtered.add(mod);
            }
        }
        return filtered;
    }

    void fetchModListAsync() {
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

                Seq<ModInfo> mods = parseModList(response.toString());
                mods.sort(m -> -parseTimestamp(m.lastUpdated));

                Core.app.post(() -> {
                    allMods = mods;
                    currentPage = 0;
                    displayCurrentPage();
                });
            } catch (Exception ex) {
                Log.err("Failed to fetch mods", ex);
                Core.app.post(() -> pageLabel.setText("[scarlet]Failed to load[]"));
            }
        });
    }

    void displayCurrentPage() {
        modList.clear();

        Seq<ModInfo> filtered = getFilteredMods();
        int start = currentPage * modsPerPage;
        int end = Math.min(start + modsPerPage, filtered.size);
        int maxPage = Math.max(0, (filtered.size - 1) / modsPerPage);

        pageLabel.setText("[accent]" + filtered.size + " mods[] • Page " + (currentPage + 1) + "/" + (maxPage + 1));

        if (filtered.isEmpty()) {
            modList.add("[scarlet]No mods found[]").pad(20f);
            return;
        }

        for (int i = start; i < end; i++) {
            buildModEntry(modList, filtered.get(i));
        }
    }

    void buildModEntry(Table table, ModInfo mod) {
        table.table(Tex.button, t -> {
            t.left().margin(8f);
            
            Image iconImg = new Image(Icon.box);
            iconImg.setScaling(Scaling.fit);
            t.add(iconImg).size(48f).pad(5f);
            
            if (mod.iconTexture == null && !mod.iconLoading) {
                mod.iconLoading = true;
                loadModIconAsync(mod, iconImg);
            } else if (mod.iconTexture != null) {
                iconImg.setDrawable(new TextureRegionDrawable(mod.iconTexture));
            }
            
            t.table(info -> {
                info.left();
                
                info.table(titleRow -> {
                    titleRow.left();
                    titleRow.add("[accent]" + mod.name + "[]").left().padRight(8f).maxWidth(200f);
                    
                    if (mod.modType != null) {
                        if (mod.modType.equals("java")) {
                            if (javaBadge != null) {
                                Image badge = new Image(javaBadge);
                                badge.setScaling(Scaling.fit);
                                titleRow.add(badge).size(28f, 16f);
                            } else {
                                titleRow.add("[#b07219]JAVA[]");
                            }
                        } else {
                            if (jsBadge != null) {
                                Image badge = new Image(jsBadge);
                                badge.setScaling(Scaling.fit);
                                titleRow.add(badge).size(28f, 16f);
                            } else {
                                titleRow.add("[#f1e05a]JS[]");
                            }
                        }
                    }
                }).growX().left().row();
                
                info.add("[lightgray]" + mod.author + "[]").left().padTop(2f).row();
                info.add("[darkgray]" + formatDate(mod.lastUpdated) + "[]").left().padTop(2f).row();
                
            }).growX().pad(5f);
            
            t.table(actions -> {
                actions.defaults().size(40f).pad(2f);
                actions.button(Icon.info, () -> showModDetails(mod)).row();
                actions.button(Icon.link, () -> Core.app.openURI(mod.repo)).row();
            }).right();
            
        }).fillX().height(80f).pad(3f).row();
        
        table.image().height(1f).growX().color(Color.darkGray).row();
    }

    void showModDetails(ModInfo mod) {
        BaseDialog detailDialog = new BaseDialog(mod.name);
        detailDialog.addCloseButton();

        Table content = new Table(Tex.button);
        content.margin(15f);

        if (mod.iconTexture != null) {
            content.image(new TextureRegionDrawable(mod.iconTexture)).size(64f).pad(8f).row();
        } else {
            content.image(Icon.box).size(64f).pad(8f).row();
        }

        Table titleTable = new Table();
        titleTable.add("[accent]" + mod.name + "[]").padRight(10f);
        
        if (mod.modType != null) {
            if (mod.modType.equals("java") && javaBadge != null) {
                titleTable.add(new Image(javaBadge)).size(40f, 24f);
            } else if (mod.modType.equals("javascript") && jsBadge != null) {
                titleTable.add(new Image(jsBadge)).size(40f, 24f);
            } else {
                titleTable.add(mod.modType.equals("java") ? "[#b07219]JAVA[]" : "[#f1e05a]JS[]");
            }
        }
        
        content.add(titleTable).row();
        content.add("[cyan]" + mod.author + "[]").pad(5f).row();

        if (mod.description != null && !mod.description.isEmpty()) {
            content.add("[lightgray]" + mod.description + "[]").width(400f).wrap().center().pad(8f).row();
        }

        content.image().height(2f).growX().color(Color.gray).pad(5f).row();

        Table statsTable = new Table();
        statsTable.add("[cyan]Loading...[]").row();
        content.add(statsTable).row();

        content.button("Open Repository", Icon.link, () -> Core.app.openURI(mod.repo)).size(200f, 45f).pad(8f);

        detailDialog.cont.add(new ScrollPane(content)).grow();
        detailDialog.show();

        fetchModStats(mod, stats -> {
            statsTable.clear();
            statsTable.defaults().left().pad(4f);
            statsTable.add("[yellow]★[] " + stats.stars + " stars").row();
            statsTable.add("[lime]↓[] " + stats.downloads + " downloads").row();
            statsTable.add("[cyan]⚡[] " + stats.releases + " releases").row();
            statsTable.add("[lightgray]Version:[] " + mod.version).row();
            statsTable.add("[darkgray]Updated:[] " + formatDate(mod.lastUpdated)).row();
        });
    }

    void fetchModStats(ModInfo mod, Cons<ModStats> callback) {
        Core.app.post(() -> {
            try {
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;

                String owner = parts[0], repo = parts[1];

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
        Core.app.post(() -> {
            try {
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;

                HttpURLConnection conn = (HttpURLConnection) new URL("https://raw.githubusercontent.com/" + parts[0] + "/" + parts[1] + "/master/icon.png").openConnection();
                conn.setRequestProperty("User-Agent", "ModInfo-Plus/1.0");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);

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
                            mod.iconTexture = new TextureRegion(texture);
                            iconImg.setDrawable(new TextureRegionDrawable(mod.iconTexture));
                            pixmap.dispose();
                        } catch (Exception e) {
                            mod.iconLoading = false;
                        }
                    });
                }
            } catch (Exception e) {
                mod.iconLoading = false;
            }
        });
    }

    Seq<ModInfo> parseModList(String json) {
        Seq<ModInfo> mods = new Seq<>();
        try {
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);

            for (String modStr : json.split("\\},\\s*\\{")) {
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
                mod.modType = detectModType(modStr);

                if (mod.repo != null && mod.name != null) mods.add(mod);
            }
        } catch (Exception e) {
            Log.err("Parse error", e);
        }
        return mods;
    }
    
    String detectModType(String modJson) {
        return (modJson.contains("\"java\":true") || modJson.contains("\"java\": true")) ? "java" : "javascript";
    }

    String extractJsonValue(String json, String key) {
        try {
            int start = json.indexOf("\"" + key + "\"");
            if (start == -1) return "";
            start = json.indexOf(":", start) + 1;
            start = json.indexOf("\"", start) + 1;
            int end = json.indexOf("\"", start);
            if (start > 0 && end > start) {
                return json.substring(start, end).replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "\\");
            }
        } catch (Exception e) {}
        return "";
    }

    long parseTimestamp(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            if (parts.length >= 3) {
                return Long.parseLong(parts[0]) * 10000000000L + Long.parseLong(parts[1]) * 100000000L + Long.parseLong(parts[2].substring(0, 2)) * 1000000L;
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
        int total = 0, index = 0;
        String search = "\"download_count\":";
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
            int start = data.indexOf("\"stargazers_count\":") + 19;
            int end = data.indexOf(",", start);
            if (end == -1) end = data.indexOf("}", start);
            return Integer.parseInt(data.substring(start, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    class ModInfo {
        String repo, name, author, description = "", version = "", lastUpdated = "";
        String modType;
        TextureRegion iconTexture;
        boolean iconLoading;
    }

    class ModStats {
        int downloads, releases, stars;
    }
}