import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import java.net.*;
import java.io.*;

public class TestMod extends Mod {
    
    public TestMod() {
        Events.on(ClientLoadEvent.class, e -> {
            // Add button to main menu
            Vars.ui.menufrag.addButton("ModInfo", Icon.info, () -> {
                showStatsDialog();
            });
        });
    }
    
    void showStatsDialog() {
        BaseDialog dialog = new BaseDialog("ModInfo v1.0");
        
        dialog.cont.add("[accent]Fetching mod stats...").row();
        
        dialog.buttons.button("Close", dialog::hide).size(120, 50);
        dialog.show();
        
        // Fetch stats in background
        fetchStats("SamielXD", "UnitNamerMod", stats -> {
            dialog.cont.clear();
            
            if (stats != null) {
                dialog.cont.add("[cyan]Unit Namer Mod").row();
                dialog.cont.add("").height(20).row();
                dialog.cont.add("[white]Downloads: [lime]" + stats.downloads).row();
                dialog.cont.add("[white]Releases: [accent]" + stats.releases).row();
                dialog.cont.add("[white]Stars: [yellow]" + stats.stars).row();
            } else {
                dialog.cont.add("[scarlet]Failed to load stats").row();
            }
        });
    }
    
    void fetchStats(String owner, String repo, Cons<ModStats> callback) {
        Core.app.post(() -> {
            try {
                // Fetch releases
                String url = "https://api.github.com/repos/" + owner + "/" + repo + "/releases";
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "ModInfo");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Simple parsing (just count releases and downloads)
                String data = response.toString();
                int releases = countOccurrences(data, "\"tag_name\"");
                int downloads = countDownloads(data);
                
                // Fetch repo info for stars
                String repoUrl = "https://api.github.com/repos/" + owner + "/" + repo;
                HttpURLConnection repoConn = (HttpURLConnection) new URL(repoUrl).openConnection();
                repoConn.setRequestMethod("GET");
                repoConn.setRequestProperty("User-Agent", "ModInfo");
                
                BufferedReader repoReader = new BufferedReader(new InputStreamReader(repoConn.getInputStream()));
                StringBuilder repoResponse = new StringBuilder();
                while ((line = repoReader.readLine()) != null) {
                    repoResponse.append(line);
                }
                repoReader.close();
                
                int stars = parseStars(repoResponse.toString());
                
                ModStats stats = new ModStats();
                stats.downloads = downloads;
                stats.releases = releases;
                stats.stars = stars;
                
                Core.app.post(() -> callback.get(stats));
                
            } catch (Exception ex) {
                Core.app.post(() -> callback.get(null));
            }
        });
    }
    
    int countOccurrences(String str, String find) {
        int count = 0;
        int index = 0;
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
                String numStr = data.substring(index, end).trim();
                total += Integer.parseInt(numStr);
            } catch (Exception e) {}
        }
        
        return total;
    }
    
    int parseStars(String data) {
        String search = "\"stargazers_count\":";
        int index = data.indexOf(search);
        if (index == -1) return 0;
        
        index += search.length();
        int end = data.indexOf(",", index);
        if (end == -1) end = data.indexOf("}", index);
        
        try {
            return Integer.parseInt(data.substring(index, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }
    
    class ModStats {
        int downloads;
        int releases;
        int stars;
    }
}