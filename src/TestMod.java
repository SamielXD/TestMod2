// Enhanced ModInfo for Mindustry 154 - Full code in one file

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
    
    public TestMod() {
        Events.on(ClientLoadEvent.class, e -> {
            Vars.ui.menufrag.addButton("ModInfo", Icon.info, () -> {
                showModBrowser();
            });
        });
    }
    
    void showModBrowser() {
        BaseDialog dialog = new BaseDialog("Mindustry Mod Browser");
        dialog.addCloseButton();
        
        Table cont = new Table();
        ScrollPane pane = new ScrollPane(cont);
        pane.setFadeScrollBars(false);
        
        dialog.cont.add("[accent]Loading mods from GitHub...").row();
        dialog.cont.add(pane).grow().row();
        dialog.show();
        
        // Fetch mod list from MindustryMods repository
        fetchModList(mods -> {
            dialog.cont.clear();
            
            if (mods == null || mods.size == 0) {
                dialog.cont.add("[scarlet]Failed to load mods").row();
                dialog.cont.add(pane).grow().row();
                return;
            }
            
            allMods = mods;
            dialog.cont.add("[accent]Found " + mods.size + " mods").padBottom(10).row();
            dialog.cont.add(pane).grow().row();
            
            cont.clear();
            cont.margin(10f);
            
            for (ModInfo mod : mods) {
                buildModEntry(cont, mod);
            }
        });
    }
    
    void buildModEntry(Table table, ModInfo mod) {
        table.table(Tex.button, t -> {
            t.margin(10f);
            
            // Left side - Icon
            Table iconTable = new Table();
            iconTable.image().size(48f).update(img -> {
                if (mod.iconTexture != null) {
                    img.setDrawable(new TextureRegionDrawable(new TextureRegion(mod.iconTexture)));
                } else if (!mod.iconLoading) {
                    img.setDrawable(Icon.book);
                }
            }).pad(4f);
            
            t.add(iconTable).size(56f).padRight(10f);
            
            // Middle - Info
            Table info = new Table();
            info.left();
            info.add("[accent]" + mod.name).left().row();
            info.add("[lightgray]" + mod.author).left().get().setFontScale(0.8f);
            info.row();
            info.add("[gray]" + (mod.description.length() > 100 ? mod.description.substring(0, 100) + "..." : mod.description))
                .left().width(300f).wrap().get().setFontScale(0.75f);
            info.row();
            info.add("[white]Stars: [yellow]" + mod.stars + " [white]Version: [cyan]" + mod.version)
                .left().get().setFontScale(0.75f);
            info.row();
            
            t.add(info).growX().padRight(10f);
            
            // Right side - Buttons
            Table buttons = new Table();
            buttons.defaults().size(120f, 40f).pad(2f);
            
            buttons.button("View Stats", Icon.info, () -> {
                showModStats(mod);
            }).row();
            
            buttons.button("GitHub", Icon.link, () -> {
                if (!Core.app.openURI(mod.repo)) {
                    Vars.ui.showErrorMessage("Could not open link!");
                }
            }).row();
            
            t.add(buttons).right();
            
        }).growX().pad(5f).row();
        
        // Load icon asynchronously
        if (mod.iconTexture == null && !mod.iconLoading) {
            mod.iconLoading = true;
            loadModIcon(mod);
        }
    }
    
    void showModStats(ModInfo mod) {
        BaseDialog statsDialog = new BaseDialog("Mod Statistics");
        
        statsDialog.cont.add("[cyan]" + mod.name).row();
        statsDialog.cont.add("").height(20).row();
        statsDialog.cont.add("[white]Author: [accent]" + mod.author).row();
        statsDialog.cont.add("[white]Repository: [accent]" + mod.repo).row();
        statsDialog.cont.add("[white]Stars: [yellow]" + mod.stars).row();
        statsDialog.cont.add("[white]Version: [cyan]" + mod.version).row();
        statsDialog.cont.add("[white]Last Updated: [lightgray]" + mod.lastUpdated).row();
        
        if (mod.description != null && !mod.description.isEmpty()) {
            statsDialog.cont.add("").height(10).row();
            statsDialog.cont.add("[lightgray]" + mod.description).width(400f).wrap().row();
        }
        
        statsDialog.buttons.button("Close", statsDialog::hide).size(120, 50);
        statsDialog.show();
    }
    
    void fetchModList(Cons<Seq<ModInfo>> callback) {
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
                Core.app.post(() -> callback.get(mods));
                
            } catch (Exception ex) {
                Log.err("Failed to fetch mod list", ex);
                Core.app.post(() -> callback.get(null));
            }
        });
    }
    
    Seq<ModInfo> parseModList(String json) {
        Seq<ModInfo> mods = new Seq<>();
        
        try {
            // Remove opening bracket
            json = json.trim();
            if (json.startsWith("[")) json = json.substring(1);
            if (json.endsWith("]")) json = json.substring(0, json.length() - 1);
            
            // Split by mod objects
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
                
                String starsStr = extractJsonValue(modStr, "stars");
                try {
                    mod.stars = Integer.parseInt(starsStr);
                } catch (Exception e) {
                    mod.stars = 0;
                }
                
                if (mod.repo != null && mod.name != null) {
                    mods.add(mod);
                }
            }
        } catch (Exception e) {
            Log.err("Failed to parse mod list", e);
        }
        
        return mods;
    }
    
    String extractJsonValue(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int start = json.indexOf(search);
            if (start == -1) return "";
            
            start = json.indexOf(":", start) + 1;
            start = json.indexOf("\"", start) + 1;
            int end = json.indexOf("\"", start);
            
            if (start > 0 && end > start) {
                return json.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            }
        } catch (Exception e) {}
        return "";
    }
    
    void loadModIcon(ModInfo mod) {
        Core.app.post(() -> {
            try {
                // Extract owner and repo name from repo URL
                String repoUrl = mod.repo.replace("https://github.com/", "");
                String[] parts = repoUrl.split("/");
                if (parts.length < 2) return;
                
                String owner = parts[0];
                String repoName = parts[1];
                
                // Try to fetch icon.png from repository
                String iconUrl = "https://raw.githubusercontent.com/" + owner + "/" + repoName + "/master/icon.png";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(iconUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ModInfo-Enhanced");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    InputStream in = conn.getInputStream();
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    
                    byte[] data = new byte[4096];
                    int n;
                    while ((n = in.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, n);
                    }
                    in.close();
                    
                    byte[] imageData = buffer.toByteArray();
                    
                    Core.app.post(() -> {
                        try {
                            Pixmap pixmap = new Pixmap(imageData, 0, imageData.length);
                            mod.iconTexture = new Texture(pixmap);
                            pixmap.dispose();
                        } catch (Exception e) {
                            Log.err("Failed to create texture for " + mod.name, e);
                        }
                    });
                }
            } catch (Exception e) {
                Log.err("Failed to load icon for " + mod.name, e);
            }
        });
    }
    
    class ModInfo {
        String repo;
        String name;
        String author;
        String description = "";
        String version = "";
        String lastUpdated = "";
        int stars;
        Texture iconTexture;
        boolean iconLoading;
    }
}