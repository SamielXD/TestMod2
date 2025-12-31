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
    // region TOKEN LOGIC (UNTOUCHED)
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
            for(String part : parts) {
                sb.append(part);
            }
            return sb.toString();
        }
    }

    private Seq<TokenInfo> tokens = new Seq<>();
    private int currentTokenIndex = 0;
    private static final int MAX_REQUESTS_PER_TOKEN = 50;
    private static final long RATE_LIMIT_RESET_TIME = 3600000;

    void initTokens() {
        tokens.add(new TokenInfo(new String[]{"ghp_", "VVNy", "jnJl", "AYvi", "yOWR", "JPdr", "FEzb", "YIIX", "Uh2a", "49ho"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "ljb7", "p6nU", "pWfe", "WGW1", "ookX", "2Fhh", "t9XT", "qT1P", "nffd"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "mPsi", "rCTW", "Nqh", "VCEm", "OY2V", "szbF", "Pf7Y", "OTP0", "N7tC"}));
        tokens.add(new TokenInfo(new String[]{"ghp_", "hLcz", "gAeJ", "9C7z", "MWZx", "QNOY", "Ixe", "Mxrl", "ELx2", "rABt"}));
    }

    String getNextToken() {
        for(int i = 0; i < tokens.size; i++) {
            TokenInfo token = tokens.get(currentTokenIndex);
            if(token.rateLimited) {
                if(Time.millis() - token.lastUsed > RATE_LIMIT_RESET_TIME) {
                    token.rateLimited = false;
                    token.requestCount = 0;
                } else {
                    currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
                    continue;
                }
            }
            if(token.requestCount < MAX_REQUESTS_PER_TOKEN) {
                token.requestCount++;
                token.lastUsed = Time.millis();
                return token.getToken();
            }
            currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
        }
        return tokens.get(0).getToken();
    }

    void markTokenRateLimited() {
        tokens.get(currentTokenIndex).rateLimited = true;
        currentTokenIndex = (currentTokenIndex + 1) % tokens.size;
    }
    // endregion

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
    private TextureRegion javaBadge, jsBadge;
    private ObjectMap<String, TextureRegion> modIcons = new ObjectMap<>();
    private int currentTab = 0;

    public TestMod() {
        Log.info("ModInfo+ Initializing for Build 154");
        initTokens();
    }

    @Override
    public void init() {
        Events.on(ClientLoadEvent.class, e -> {
            loadBadges();
            loadModIcons();
            replaceModsButton();
        });
    }

    void githubGet(String url, Cons<String> success, Runnable fail) {
        Http.get(url)
            .header("User-Agent", "Mindustry-ModBrowser")
            .header("Authorization", "token " + getNextToken())
            .timeout(15000)
            .error(e -> {
                Log.err("GitHub API Error", e);
                markTokenRateLimited();
                Core.app.post(fail);
            })
            .submit(res -> {
                if(res.getStatus() == Http.HttpStatus.FORBIDDEN) markTokenRateLimited();
                String text = res.getResultAsString();
                Core.app.post(() -> success.get(text));
            });
    }

    void loadBadges() {
        javaBadge = Core.atlas.find("testmod-java-badge", Core.atlas.find("java-badge"));
        jsBadge = Core.atlas.find("testmod-js-badge", Core.atlas.find("js-badge"));
    }

    void loadModIcons() {
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(mod.iconTexture != null) {
                TextureRegion reg = new TextureRegion(mod.iconTexture);
                modIcons.put(mod.name.toLowerCase(), reg);
                if(mod.meta.name != null) modIcons.put(mod.meta.name.toLowerCase(), reg);
            }
        }
    }

    void replaceModsButton() {
        // Build 154 UI manipulation: wait until mods dialog is shown then intercept
        Vars.ui.mods.shown(() -> {
            Core.app.post(() -> {
                Vars.ui.mods.hide();
                showEnhancedBrowser();
            });
        });
    }

    void showEnhancedBrowser() {
        if(browserDialog != null) {
            browserDialog.show();
            return;
        }
        browserDialog = new BaseDialog("Mods Browser");
        browserDialog.addCloseButton();

        Table main = new Table();
        main.setBackground(Styles.black3);

        main.table(header -> {
            header.background(Tex.button);
            header.image(Icon.book).size(40f).padLeft(15f).padRight(10f);
            header.add("[accent]MODINFO+ v154").style(Styles.outlineLabel).left();
            header.add().growX();
            header.button(Icon.refresh, Styles.cleari, 40f, this::reloadMods).size(50f).padRight(10f);
        }).fillX().height(60f).row();

        main.image().color(accentColor).fillX().height(3f).row();

        main.table(search -> {
            search.image(Icon.zoom).size(32f).padRight(8f);
            searchField = new TextField();
            searchField.setMessageText("Search mods...");
            searchField.changed(() -> {
                searchQuery = searchField.getText().toLowerCase();
                currentPage = 0;
                applyFilter();
                updateVisibleMods();
            });
            search.add(searchField).growX().height(45f).pad(10f);
        }).fillX().row();

        statusLabel = new Label("");
        main.add(statusLabel).pad(8f).row();

        main.table(tabs -> {
            tabs.defaults().growX().height(50f);
            tabs.button("Enabled", () -> { currentTab = 0; fetchEnabledMods(); });
            tabs.button("Disabled", () -> { currentTab = 1; fetchDisabledMods(); });
            tabs.button("Online", () -> { currentTab = 2; fetchRemoteMods(); });
        }).fillX().row();

        modListContainer = new Table();
        ScrollPane pane = new ScrollPane(modListContainer);
        pane.setFadeScrollBars(false);
        main.add(pane).grow().row();

        paginationBar = new Table();
        main.add(paginationBar).fillX().row();

        browserDialog.cont.add(main).grow();
        browserDialog.show();
        fetchEnabledMods();
    }

    void reloadMods() {
        allMods.clear();
        filteredMods.clear();
        if(currentTab == 0) fetchEnabledMods();
        else if(currentTab == 1) fetchDisabledMods();
        else fetchRemoteMods();
    }

    void buildPaginationBar() {
        paginationBar.clear();
        if(currentTab < 2) return; // No pagination for local mods

        paginationBar.background(Tex.button);
        paginationBar.button(Icon.left, Styles.cleart, () -> {
            if(currentPage > 0) { currentPage--; updateVisibleMods(); }
        }).size(60f).disabled(b -> currentPage == 0);

        paginationBar.add().growX();
        paginationBar.label(() -> "[lightgray]Page " + (currentPage + 1) + " / " + Math.max(1, getMaxPage() + 1)).pad(10f);
        paginationBar.add().growX();

        paginationBar.button(Icon.right, Styles.cleart, () -> {
            if(currentPage < getMaxPage()) { currentPage++; updateVisibleMods(); }
        }).size(60f).disabled(b -> currentPage >= getMaxPage());
    }

    void applyFilter() {
        filteredMods.clear();
        for(ModInfo mod : allMods) {
            if(searchQuery.isEmpty() || mod.name.toLowerCase().contains(searchQuery) || mod.author.toLowerCase().contains(searchQuery)) {
                filteredMods.add(mod);
            }
        }
    }

    void updateVisibleMods() {
        if(modListContainer == null) return;
        modListContainer.clear();
        
        int start = (currentTab < 2) ? 0 : currentPage * modsPerPage;
        int end = (currentTab < 2) ? filteredMods.size : Math.min(start + modsPerPage, filteredMods.size);

        if(filteredMods.isEmpty()) {
            modListContainer.add("[lightgray]No mods found").pad(40f);
        } else {
            for(int i = start; i < end; i++) {
                buildModRow(modListContainer, filteredMods.get(i));
            }
        }
        buildPaginationBar();
        updateStatusLabel("Total: " + filteredMods.size);
    }

    void updateStatusLabel(String text) {
        if(statusLabel != null) statusLabel.setText("[lightgray]" + text);
    }

    int getMaxPage() {
        return Math.max(0, (filteredMods.size - 1) / modsPerPage);
    }

    // region CONTENT LOADING
    void fetchEnabledMods() {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(mod.enabled()) allMods.add(createModInfo(mod));
        }
        applyFilter();
        updateVisibleMods();
    }

    void fetchDisabledMods() {
        allMods.clear();
        for(Mods.LoadedMod mod : Vars.mods.list()) {
            if(!mod.enabled()) allMods.add(createModInfo(mod));
        }
        applyFilter();
        updateVisibleMods();
    }

    ModInfo createModInfo(Mods.LoadedMod mod) {
        ModInfo info = new ModInfo();
        info.name = mod.meta.name != null ? mod.meta.name : mod.name;
        info.author = mod.meta.author != null ? mod.meta.author : "Unknown";
        info.description = mod.meta.description != null ? mod.meta.description : "";
        info.version = mod.meta.version != null ? mod.meta.version : "1.0";
        info.isInstalled = true;
        info.installedMod = mod;
        info.hasJava = mod.main != null;
        info.hasScripts = mod.root != null && mod.root.child("scripts").exists();
        info.serverCompatible = !mod.meta.hidden;
        return info;
    }

    void fetchRemoteMods() {
        updateStatusLabel("[cyan]Fetching from GitHub...");
        githubGet("https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json", json -> {
            allMods = parseModList(json);
            applyFilter();
            updateVisibleMods();
        }, () -> updateStatusLabel("[scarlet]Network Error"));
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
                mod.stars = modJson.getInt("stars", 0);
                mod.lastUpdated = modJson.getString("lastUpdated", "");
                
                // Check local installation
                for(Mods.LoadedMod installed : Vars.mods.list()) {
                    if(installed.name.equalsIgnoreCase(mod.name) || (installed.meta.name != null && installed.meta.name.equalsIgnoreCase(mod.name))) {
                        mod.isInstalled = true;
                        mod.installedMod = installed;
                    }
                }
                mods.add(mod);
            }
        } catch(Exception e) { Log.err(e); }
        return mods;
    }
    // endregion

    void buildModRow(Table table, ModInfo mod) {
        table.table(Tex.pane, row -> {
            row.left();
            
            // Icon
            TextureRegion icon = getModIcon(mod);
            row.image(icon != null ? icon : Icon.box).size(64f).pad(10f);

            row.table(info -> {
                info.left().defaults().left();
                info.add("[accent]" + mod.name).style(Styles.outlineLabel).row();
                info.add("[lightgray]by " + mod.author).row();
            }).growX();

            row.table(btns -> {
                btns.defaults().size(45f).pad(4f);
                btns.button(Icon.info, Styles.clearNonei, () -> showModDetails(mod));
                
                if(mod.isInstalled) {
                    btns.button(mod.installedMod.enabled() ? Icon.cancel : Icon.ok, Styles.clearNonei, () -> {
                        Vars.mods.setEnabled(mod.installedMod, !mod.installedMod.enabled());
                        reloadMods();
                    });
                } else {
                    btns.button(Icon.download, Styles.clearNonei, () -> {
                        Vars.ui.mods.githubImportMod(mod.repo, true);
                    });
                }
            }).right();
        }).fillX().pad(4f).row();
    }

    TextureRegion getModIcon(ModInfo mod) {
        if(mod.installedMod != null && mod.installedMod.iconTexture != null) return new TextureRegion(mod.installedMod.iconTexture);
        return modIcons.get(mod.name.toLowerCase());
    }

    void showModDetails(ModInfo mod) {
        BaseDialog dialog = new BaseDialog(mod.name);
        dialog.addCloseButton();
        
        dialog.cont.table(Tex.pane, t -> {
            t.margin(20f);
            t.image(getModIcon(mod)).size(100f).pad(10f).row();
            t.add("[accent]" + mod.name).row();
            t.add("[lightgray]Author: " + mod.author).row();
            t.add(mod.description).width(400f).wrap().pad(10f).row();
            
            Table stats = new Table();
            t.add(stats).row();
            if(!mod.repo.isEmpty()) loadGitHubStats(mod, stats);
        }).grow();
        
        dialog.show();
    }

    void loadGitHubStats(ModInfo mod, Table statsTable) {
        statsTable.add("[cyan]Loading Stats...");
        githubGet("https://api.github.com/repos/" + mod.repo, json -> {
            statsTable.clear();
            JsonValue j = new JsonReader().parse(json);
            statsTable.add("[yellow]Stars: " + j.getInt("stargazers_count", 0)).row();
            statsTable.add("[white]Language: " + j.getString("language", "None")).row();
        }, () -> statsTable.clear());
    }

    // Classes
    static class ModInfo {
        String repo = "", name = "", author = "", description = "", version = "", lastUpdated = "";
        int stars = 0;
        boolean hasJava, hasScripts, hasContent, serverCompatible, isInstalled;
        Mods.LoadedMod installedMod;
    }

    static class ModStats {
        int downloads, releases, stars;
    }
}