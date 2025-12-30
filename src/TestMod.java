
import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import java.net.*;
import java.io.*;

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
            for(String part : parts){
                sb.append(part);
            }
            return sb.toString();
        }
    }

    private Seq<TokenInfo> tokens = new Seq<>();
    private int currentTokenIndex = 0;
    private static final int MAX_REQUESTS_PER_TOKEN = 50;
    private static final long RATE_LIMIT_RESET_TIME = 3600000L;

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

    private Color accentColor = Color.valueOf("ffd37f");
    private TextureRegion javaBadge;
    private TextureRegion jsBadge;
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();

    private int currentTab = 0;

    public TestMod(){
        Log.info("ModInfo+ Initializing");
        initTokens();
    }

    void initTokens(){
        tokens.add(new TokenInfo(new String[]{"ghp_", "VVNy", "jnJl", "AYvi", "yOWR", "JPdr", "FEzb", "YIIX", "Uh2a", "49ho"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "ljb7", "p6nU", "pWfe", "WGW1", "ookX", "2Fhh", "t9XT", "qT1P", "nffd"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "mPsi", "rCTW", "Nqh", "VCEm", "OY2V", "szbF", "Pf7Y", "OTP0", "N7tC"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "hLcz", "gAeJ", "9C7z", "MWZx", "QNOY", "Ixe", "Mxrl", "ELx2", "rABt"}));
    }

    String getNextToken(){
        for(int tries = 0; tries < tokens.size; tries++){
            TokenInfo token = tokens.get(currentTokenIndex);

            if(token.rateLimited){
                if(Time.millis() - token.lastUsed > RATE_LIMIT_RESET_TIME){
                    token.rateLimited = false;
                    token.requestCount = 0;
                }else{
                    currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
                    continue;
                }
            }

            if(token.requestCount < MAX_REQUESTS_PER_TOKEN){
                token.requestCount++;
                token.lastUsed = Time.millis();
                return token.getToken();
            }

            currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
        }

        return tokens.get(currentTokenIndex).getToken();
    }

    void markTokenRateLimited(){
        tokens.get(currentTokenIndex).rateLimited = true;
        tokens.get(currentTokenIndex).lastUsed = Time.millis();
        currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
    }

    void githubGet(String url, Cons<String> success, Runnable fail){
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
    public void init(){
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                loadBadges();
                loadModIcons();
                replaceModsButton();
            });
        });
    }void loadBadges(){
        javaBadge = Core.atlas.find("java");
        jsBadge = Core.atlas.find("js");
    }

    void loadModIcons(){
        for(mindustry.mod.Mods.LoadedMod mod : Vars.mods.list()){
            if(mod.icon != null){
                modIcons.put(
                    mod.meta.displayName,
                    mod.icon
                );
            }
        }
    }

    void replaceModsButton(){
        for(Element e : Vars.ui.menuGroup.getChildren()){
            if(e instanceof TextButton){
                TextButton b = (TextButton)e;
                if(b.getText().toString().equals(Core.bundle.get("mods"))){
                    b.clicked(this::showBrowser);
                }
            }
        }
    }

    void showBrowser(){
        if(browserDialog == null){
            browserDialog = new BaseDialog("Mod Browser+");
            browserDialog.addCloseButton();

            browserDialog.cont.pane(p -> {
                p.top().left();

                p.table(top -> {
                    searchField = new TextField();
                    searchField.setMessageText("Search mods...");
                    searchField.changed(() -> {
                        searchQuery = searchField.getText().toLowerCase();
                        applyFilter();
                    });

                    top.add(searchField).growX().pad(4);
                }).growX().row();

                modListContainer = new Table();
                p.add(modListContainer).growX().row();

                paginationBar = new Table();
                p.add(paginationBar).growX().padTop(6);
            }).grow().pad(6);

            statusLabel = new Label("");
            browserDialog.cont.add(statusLabel).left().pad(4);

            fetchMods();
        }

        browserDialog.show();
    }

    void fetchMods(){
        statusLabel.setText("Loading mods...");
        allMods.clear();

        githubGet(
            "https://api.github.com/search/repositories?q=mindustry+mod&per_page=100",
            res -> {
                try{
                    JsonValue root = new JsonReader().parse(res);
                    for(JsonValue repo : root.get("items")){
                        ModInfo info = new ModInfo();
                        info.name = repo.getString("name");
                        info.author = repo.get("owner").getString("login");
                        info.repo = repo.getString("html_url");
                        info.stars = repo.getInt("stargazers_count", 0);
                        info.language = repo.getString("language", "Java");

                        allMods.add(info);
                    }
                    applyFilter();
                    statusLabel.setText("");
                }catch(Exception e){
                    statusLabel.setText("Failed to parse mods");
                }
            },
            () -> statusLabel.setText("GitHub rate limit hit")
        );
    }

    void applyFilter(){
        filteredMods.clear();

        for(ModInfo mod : allMods){
            if(searchQuery.isEmpty() ||
               mod.name.toLowerCase().contains(searchQuery) ||
               mod.author.toLowerCase().contains(searchQuery)){
                filteredMods.add(mod);
            }
        }

        currentPage = 0;
        rebuildList();
    }

    void rebuildList(){
        modListContainer.clear();

        int start = currentPage * modsPerPage;
        int end = Math.min(start + modsPerPage, filteredMods.size);

        for(int i = start; i < end; i++){
            ModInfo mod = filteredMods.get(i);

            modListContainer.table(t -> {
                t.left().top();

                TextureRegion icon = modIcons.get(mod.name);
                if(icon != null){
                    t.image(icon).size(48).padRight(6);
                }

                t.table(info -> {
                    info.left();
                    info.add(mod.name).color(accentColor).row();
                    info.add("by " + mod.author).color(Color.lightGray).row();
                    info.add("★ " + mod.stars).row();
                }).growX();

                if("Java".equals(mod.language)){
                    t.image(javaBadge).size(20);
                }else{
                    t.image(jsBadge).size(20);
                }
            }).growX().pad(4).row();
        }

        rebuildPagination();
    }

    void rebuildPagination(){
        paginationBar.clear();

        int maxPage = (filteredMods.size - 1) / modsPerPage;

        paginationBar.button("<", () -> {
            if(currentPage > 0){
                currentPage--;
                rebuildList();
            }
        });

        paginationBar.add("Page " + (currentPage + 1) + " / " + (maxPage + 1)).pad(6);

        paginationBar.button(">", () -> {
            if(currentPage < maxPage){
                currentPage++;
                rebuildList();
            }
        });
    }static class ModInfo {
        String name;
        String author;
        String repo;
        int stars;
        String language;
    }

    static class ModStats {
        int downloads;
        int lastUpdate;
    }

    ModStats getStats(ModInfo info){
        if(statsCache.containsKey(info.repo)){
            return statsCache.get(info.repo);
        }

        ModStats stats = new ModStats();
        stats.downloads = 0;
        stats.lastUpdate = 0;
        statsCache.put(info.repo, stats);
        return stats;
    }
}