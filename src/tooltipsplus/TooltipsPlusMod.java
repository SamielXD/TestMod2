package tooltipsplus;

import arc.Events;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

public class TooltipsPlusMod extends Mod {

    boolean enabled = true;
    boolean showPowerDetails = true;
    boolean showItemFlow = true;
    boolean showUnitAdvanced = true;
    boolean compactMode = false;
    boolean showWarnings = true;
    
    boolean showTurretInfo = true;
    boolean showConnectionInfo = true;
    boolean showDrillInfo = true;
    boolean showTeamStats = true;
    boolean showRepairInfo = true;
    boolean showStorageBreakdown = true;
    boolean showProductionHistory = true;
    boolean comparisonMode = false;
    
    int tooltipOpacity = 8;
    boolean followCursor = true;
    boolean showIcons = true;
    int fontSize = 1;
    String colorTheme = "default";
    boolean playHoverSound = false;
    boolean highlightHovered = true;
    boolean showOnMinimap = false;
    int maxTooltipLines = 20;
    
    Table tooltipTable;
    Building lastHoveredBuilding;
    Unit lastHoveredUnit;
    float hoverTimer = 0f;
    float hoverDelay = 0.15f;
    
    Building pinnedBuilding = null;
    boolean isPinned = false;
    
    float[] productionHistory = new float[60];
    int historyIndex = 0;
    float lastHealth = 0f;
    
    Interval updateInterval = new Interval(2);
    float[] buildingStats = new float[10];
    
    String statColor = "[stat]";
    String accentColor = "[accent]";
    String warningColor = "[scarlet]";
    String successColor = "[lime]";
    String infoColor = "[lightgray]";
    
    public TooltipsPlusMod() {
        loadSettings();
    }

    void loadSettings() {
        enabled = arc.Core.settings.getBool("ttp-enabled", true);
        showPowerDetails = arc.Core.settings.getBool("ttp-power", true);
        showItemFlow = arc.Core.settings.getBool("ttp-itemflow", true);
        showUnitAdvanced = arc.Core.settings.getBool("ttp-unitadv", true);
        compactMode = arc.Core.settings.getBool("ttp-compact", false);
        showWarnings = arc.Core.settings.getBool("ttp-warnings", true);
        
        showTurretInfo = arc.Core.settings.getBool("ttp-turret", true);
        showConnectionInfo = arc.Core.settings.getBool("ttp-connections", true);
        showDrillInfo = arc.Core.settings.getBool("ttp-drill", true);
        showTeamStats = arc.Core.settings.getBool("ttp-team", true);
        showRepairInfo = arc.Core.settings.getBool("ttp-repair", true);
        showStorageBreakdown = arc.Core.settings.getBool("ttp-storage", true);
        showProductionHistory = arc.Core.settings.getBool("ttp-history", true);
        
        tooltipOpacity = arc.Core.settings.getInt("ttp-opacity", 8);
        followCursor = arc.Core.settings.getBool("ttp-follow", true);
        showIcons = arc.Core.settings.getBool("ttp-icons", true);
        fontSize = arc.Core.settings.getInt("ttp-fontsize", 1);
        colorTheme = arc.Core.settings.getString("ttp-theme", "default");
        playHoverSound = arc.Core.settings.getBool("ttp-sound", false);
        highlightHovered = arc.Core.settings.getBool("ttp-highlight", true);
        showOnMinimap = arc.Core.settings.getBool("ttp-minimap", false);
        maxTooltipLines = arc.Core.settings.getInt("ttp-maxlines", 20);
        
        applyColorTheme();
    }

    void saveSettings() {
        arc.Core.settings.put("ttp-enabled", enabled);
        arc.Core.settings.put("ttp-power", showPowerDetails);
        arc.Core.settings.put("ttp-itemflow", showItemFlow);
        arc.Core.settings.put("ttp-unitadv", showUnitAdvanced);
        arc.Core.settings.put("ttp-compact", compactMode);
        arc.Core.settings.put("ttp-warnings", showWarnings);
        
        arc.Core.settings.put("ttp-turret", showTurretInfo);
        arc.Core.settings.put("ttp-connections", showConnectionInfo);
        arc.Core.settings.put("ttp-drill", showDrillInfo);
        arc.Core.settings.put("ttp-team", showTeamStats);
        arc.Core.settings.put("ttp-repair", showRepairInfo);
        arc.Core.settings.put("ttp-storage", showStorageBreakdown);
        arc.Core.settings.put("ttp-history", showProductionHistory);
        
        arc.Core.settings.put("ttp-opacity", tooltipOpacity);
        arc.Core.settings.put("ttp-follow", followCursor);
        arc.Core.settings.put("ttp-icons", showIcons);
        arc.Core.settings.put("ttp-fontsize", fontSize);
        arc.Core.settings.put("ttp-theme", colorTheme);
        arc.Core.settings.put("ttp-sound", playHoverSound);
        arc.Core.settings.put("ttp-highlight", highlightHovered);
        arc.Core.settings.put("ttp-minimap", showOnMinimap);
        arc.Core.settings.put("ttp-maxlines", maxTooltipLines);
        
        arc.Core.settings.forceSave();
    }

    @Override
    public void init() {
        Log.info("TooltipsPlus v3.0 initializing...");
        if (enabled) {
            setupTooltipSystem();
            setupHotkeys();
            injectStaticDescriptions();
        }
        addSettingsUI();
        Log.info("TooltipsPlus loaded successfully with all features");
    }

    void applyColorTheme() {
        switch(colorTheme) {
            case "dark":
                statColor = "[gray]";
                accentColor = "[lightgray]";
                warningColor = "[orange]";
                successColor = "[green]";
                infoColor = "[darkgray]";
                break;
            case "neon":
                statColor = "[cyan]";
                accentColor = "[magenta]";
                warningColor = "[red]";
                successColor = "[lime]";
                infoColor = "[sky]";
                break;
            case "minimal":
                statColor = "";
                accentColor = "";
                warningColor = "[scarlet]";
                successColor = "[lime]";
                infoColor = "[lightgray]";
                break;
            default:
                statColor = "[stat]";
                accentColor = "[accent]";
                warningColor = "[scarlet]";
                successColor = "[lime]";
                infoColor = "[lightgray]";
                break;
        }
    }

    void setupHotkeys() {
        Events.run(EventType.Trigger.update, () -> {
            if (arc.Core.input.keyTap(arc.input.KeyCode.t)) {
                enabled = !enabled;
                saveSettings();
                Vars.ui.hudGroup.fill(t -> {
                    t.label(() -> enabled ? "[lime]Tooltips ON" : "[scarlet]Tooltips OFF")
                     .pad(10f);
                });
                Time.run(120f, () -> Vars.ui.hudGroup.clear());
            }
            
            if (arc.Core.input.keyTap(arc.input.KeyCode.p) && lastHoveredBuilding != null) {
                if (pinnedBuilding == lastHoveredBuilding) {
                    pinnedBuilding = null;
                    isPinned = false;
                } else {
                    pinnedBuilding = lastHoveredBuilding;
                    isPinned = true;
                }
            }
        });
    }void setupTooltipSystem() {
    tooltipTable = new Table(Styles.black);
    tooltipTable.background(Tex.buttonEdge3);
    tooltipTable.margin(6f);
    tooltipTable.visible = false;
    Vars.ui.hudGroup.addChild(tooltipTable);

    Events.run(EventType.Trigger.draw, () -> {
        if (!enabled || Vars.state.isMenu()) {
            tooltipTable.visible = false;
            hoverTimer = 0f;
            return;
        }

        if (updateInterval.get(0, 60f)) {
            updateProductionTracking();
        }

        Vec2 mousePos = arc.Core.input.mouseWorld();

        Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
        Building hoveredBuilding = (hoverTile != null) ? hoverTile.build : null;

        Unit hoveredUnit = Groups.unit.find(u -> u.within(mousePos.x, mousePos.y, u.hitSize / 2f));

        if (isPinned && pinnedBuilding != null) {
            showBuildingTooltip(pinnedBuilding);
            return;
        }

        if (hoveredBuilding != lastHoveredBuilding || hoveredUnit != lastHoveredUnit) {
            hoverTimer = 0f;
            lastHoveredBuilding = hoveredBuilding;
            lastHoveredUnit = hoveredUnit;

            if (playHoverSound && (hoveredBuilding != null || hoveredUnit != null)) {
                Sounds.click.play(0.3f);
            }
        }

        if (hoveredBuilding != null || hoveredUnit != null) {
            hoverTimer += Time.delta / 60f;
        }

        if (highlightHovered && hoveredBuilding != null) {
            Lines.stroke(2f);
            Draw.color(arc.graphics.Color.cyan, 0.6f);
            Lines.rect(
                hoveredBuilding.x - hoveredBuilding.block.size * 4f,
                hoveredBuilding.y - hoveredBuilding.block.size * 4f,
                hoveredBuilding.block.size * 8f,
                hoveredBuilding.block.size * 8f
            );
            Draw.reset();
        }

        if (hoverTimer >= hoverDelay) {
            if (hoveredBuilding != null) {
                showBuildingTooltip(hoveredBuilding);
                return;
            } else if (hoveredUnit != null) {
                showUnitTooltip(hoveredUnit);
                return;
            }
        }

        tooltipTable.visible = false;
    });
}

void updateProductionTracking() {
    if (lastHoveredBuilding != null && lastHoveredBuilding.block instanceof GenericCrafter) {
        float status = lastHoveredBuilding.power != null ? lastHoveredBuilding.power.status : 0f;
        productionHistory[historyIndex] = status;
        historyIndex = (historyIndex + 1) % 60;
    }
}

String repeat(String str, int count) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < count; i++) sb.append(str);
    return sb.toString();
}

void showBuildingTooltip(Building build) {
    tooltipTable.clear();
    tooltipTable.visible = true;

    Table titleRow = new Table();
    if (showIcons && build.block.fullIcon != null) {
        titleRow.image(build.block.fullIcon).size(24f * (fontSize + 1)).padRight(4f);
    }
    titleRow.add(accentColor + build.block.localizedName).style(Styles.outlineLabel);
    tooltipTable.add(titleRow).left().row();

    if (!compactMode) {
        tooltipTable.add(infoColor + repeat("─", 20)).padTop(2f).padBottom(2f).row();
    }

    float healthPercent = (build.health / build.maxHealth) * 100f;
    String healthColor = getPercentColor(healthPercent);
    String healthBar = makeProgressBar(build.health, build.maxHealth, 10);

    tooltipTable.add(Icon.defense + statColor + "HP: " + healthColor + (int)build.health + infoColor + "/" + (int)build.maxHealth).left().row();

    if (!compactMode && healthPercent < 100f) {
        tooltipTable.add("  " + healthBar).left().row();
    }

    if (showWarnings && healthPercent < 30f) {
        tooltipTable.add("  " + warningColor + "⚠ Critical Damage!").left().row();
    }

    if (build.power != null && showPowerDetails) {
        addPowerInfo(build);
    }

    if (build.items != null && showStorageBreakdown) {
        addItemStorageInfo(build);
    }

    if (build.liquids != null) {
        addLiquidInfo(build); // <- fixed version handles 1.54
    }

    if (showItemFlow && (build.block instanceof GenericCrafter || build.block instanceof Drill)) {
        addProductionInfo(build);
    }

    if (showTurretInfo && build.block instanceof Turret) {
        addTurretInfo(build);
    }

    if (showDrillInfo && build.block instanceof Drill) {
        addDrillInfo(build);
    }

    if (showConnectionInfo) {
        addConnectionInfo(build);
    }

    if (showPowerDetails && build.power != null) {
        addPowerNetworkInfo(build);
    }

    addConveyorFlowInfo(build);

    if (showTeamStats) {
        addTeamInfo(build);
    }

    if (isPinned) {
        tooltipTable.add("[royal]📌 PINNED (P to unpin)").left().padTop(4f).row();
    }

    positionTooltip();
    tooltipTable.pack();
}void addTeamInfo(Building build) {
        if (!Vars.state.rules.pvp) return;
        
        tooltipTable.add(statColor + "─ Team ─").padTop(4f).row();
        tooltipTable.add("  " + build.team.emoji + " " + build.team.coloredName()).left().row();
        
        if (build.team != Vars.player.team()) {
            tooltipTable.add("  " + warningColor + "🛡 Enemy Structure").left().row();
        }
    }

    void addConnectionInfo(Building build) {
        int inputBuildings = 0;
        int outputBuildings = 0;
        
        for (Building nearby : build.proximity) {
            if (nearby.block.outputsItems() || nearby.block instanceof Conveyor) {
                inputBuildings++;
            }
            if (nearby.block.hasItems || nearby.block instanceof Conveyor) {
                outputBuildings++;
            }
        }
        
        if (inputBuildings > 0 || outputBuildings > 0) {
            tooltipTable.add(statColor + "─ Connections ─").padTop(4f).row();
            if (inputBuildings > 0) {
                tooltipTable.add("  ← " + infoColor + "Inputs: " + accentColor + inputBuildings).left().row();
            }
            if (outputBuildings > 0) {
                tooltipTable.add("  → " + infoColor + "Outputs: " + accentColor + outputBuildings).left().row();
            }
        }
    }

    void addConveyorFlowInfo(Building build) {
        if (build.block instanceof Conveyor) {
            Conveyor conveyor = (Conveyor)build.block;
            float itemsPerSec = conveyor.speed * 60f;
            tooltipTable.add("→" + statColor + "Flow: " + accentColor + Strings.autoFixed(itemsPerSec, 1) + " items/s").left().row();
        }
        
        if (build.block instanceof LiquidBlock) {
            tooltipTable.add("→" + statColor + "Liquid Conduit").left().row();
        }
    }

    void addPowerNetworkInfo(Building build) {
        if (build.power != null && build.power.graph != null) {
            var graph = build.power.graph;
            float production = graph.getPowerProduced() * 60f;
            float consumption = graph.getPowerNeeded() * 60f;
            float balance = production - consumption;
            
            if (production > 0 || consumption > 0) {
                tooltipTable.add(infoColor + "─ Power Grid ─").padTop(4f).row();
                tooltipTable.add(statColor + "Production: " + accentColor + Strings.autoFixed(production, 1) + "/s").left().row();
                tooltipTable.add(statColor + "Usage: " + accentColor + Strings.autoFixed(consumption, 1) + "/s").left().row();
                
                String balanceColor = balance > 0 ? successColor : warningColor;
                String balanceSymbol = balance > 0 ? "+" : "";
                tooltipTable.add(statColor + "Balance: " + balanceColor + balanceSymbol + Strings.autoFixed(balance, 1) + "/s").left().row();
                
                if (showWarnings && balance < 0) {
                    float deficit = Math.abs(balance / production * 100f);
                    tooltipTable.add("  " + warningColor + "⚠ Overloaded " + (int)deficit + "%").left().row();
                }
            }
        }
    }

    void showUnitTooltip(Unit unit) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        Table titleRow = new Table();
        if (showIcons && unit.type.fullIcon != null) {
            titleRow.image(unit.type.fullIcon).size(24f * (fontSize + 1)).padRight(4f);
        }
        titleRow.add(accentColor + unit.type.localizedName).style(Styles.outlineLabel);
        tooltipTable.add(titleRow).left().row();
        
        if (!compactMode) {
            tooltipTable.add(infoColor + repeat("─", 20)).padTop(2f).padBottom(2f).row();
        }
        
        float healthPercent = (unit.health / unit.maxHealth) * 100f;
        String healthColor = healthPercent > 75f ? successColor : healthPercent > 40f ? "[yellow]" : warningColor;
        String icon = showIcons ? Icon.defense.toString() : "";
        
        tooltipTable.add(icon + statColor + "HP: " + healthColor + (int)unit.health + infoColor + "/" + (int)unit.maxHealth).left().row();
        
        if (unit.type.armor > 0) {
            tooltipTable.add("  " + infoColor + "Armor: " + accentColor + (int)unit.type.armor).left().row();
        }
        
        if (showWarnings && healthPercent < 25f) {
            tooltipTable.add("  " + warningColor + "⚠ Critical HP!").left().row();
        }
        
        if (showUnitAdvanced) {
            String moveIcon = unit.type.flying ? "✈" : "⛏";
            tooltipTable.add(moveIcon + statColor + "Speed: " + accentColor + Strings.autoFixed(unit.type.speed * 60f, 1)).left().row();
            
            if (unit.type.flying) {
                tooltipTable.add("  [sky]Flying Unit").left().row();
            }
        }
        
        if (showUnitAdvanced) {
            if (unit.type.mineSpeed > 0) {
                tooltipTable.add("⛏" + statColor + "Mine: " + accentColor + Strings.autoFixed(unit.type.mineSpeed, 1) + "/s").left().row();
                
                if (unit.type.mineTier > 0) {
                    tooltipTable.add("  " + infoColor + "Tier: " + accentColor + unit.type.mineTier).left().row();
                }
            }
            
            if (unit.type.buildSpeed > 0) {
                tooltipTable.add("🔨" + statColor + "Build: " + accentColor + Strings.autoFixed(unit.type.buildSpeed, 1) + "/s").left().row();
            }
            
            if (unit.type.itemCapacity > 0) {
                int carrying = unit.stack != null && unit.stack.item != null ? unit.stack.amount : 0;
                String carryColor = carrying > 0 ? accentColor : infoColor;
                tooltipTable.add("📦" + statColor + "Carry: " + carryColor + carrying + infoColor + "/" + unit.type.itemCapacity).left().row();
                
                if (unit.stack != null && unit.stack.item != null && carrying > 0) {
                    tooltipTable.add("  " + infoColor + unit.stack.item.emoji() + " " + unit.stack.item.localizedName).left().row();
                }
            }
        }
        
        if (showUnitAdvanced && unit.type.weapons.size > 0) {
            addWeaponInfo(unit);
        }
        
        addUnitStatusInfo(unit);
        
        if (Vars.state.rules.pvp && unit.team != Vars.player.team()) {
            tooltipTable.add(warningColor + "Enemy Unit").left().row();
        } else if (unit.isPlayer()) {
            tooltipTable.add("[royal]Player Unit").left().row();
        }
        
        positionTooltip();
        tooltipTable.pack();
    }

    void addWeaponInfo(Unit unit) {
        float totalDPS = 0f;
        for (var weapon : unit.type.weapons) {
            if (weapon.bullet != null) {
                float dps = weapon.bullet.damage * (60f / weapon.reload);
                totalDPS += dps;
            }
        }
        
        if (totalDPS > 0) {
            tooltipTable.add("⚔" + statColor + "DPS: " + accentColor + Strings.autoFixed(totalDPS, 1)).left().row();
            
            if (unit.type.weapons.size > 1) {
                tooltipTable.add("  " + infoColor + "Weapons: " + accentColor + unit.type.weapons.size).left().row();
            }
        }
    }

    void addUnitStatusInfo(Unit unit) {
        if (unit.mineTile != null) {
            tooltipTable.add(statColor + "⛏ Mining...").left().row();
        } else if (unit.activelyBuilding()) {
            tooltipTable.add(statColor + "🔨 Building...").left().row();
        } else if (unit.isShooting) {
            tooltipTable.add(statColor + "⚔ Combat").left().row();
        } else if (unit.moving()) {
            tooltipTable.add(statColor + "→ Moving").left().row();
        }
    }

    void positionTooltip() {
        Vec2 screenPos = arc.Core.input.mouse();
        float x = screenPos.x + 20f;
        float y = screenPos.y + 20f;
        
        if (x + tooltipTable.getWidth() > arc.Core.graphics.getWidth()) {
            x = screenPos.x - tooltipTable.getWidth() - 10f;
        }
        if (y + tooltipTable.getHeight() > arc.Core.graphics.getHeight()) {
            y = screenPos.y - tooltipTable.getHeight() - 10f;
        }
        
        if (followCursor) {
            tooltipTable.setPosition(x, y);
        } else {
            tooltipTable.setPosition(
                arc.Core.graphics.getWidth() - tooltipTable.getWidth() - 20f,
                20f
            );
        }
        
        tooltipTable.color.a = tooltipOpacity / 10f;
    }

    void injectStaticDescriptions() {
        for (Block block : Vars.content.blocks()) {
            if (block.description != null && !block.description.contains("§")) {
                StringBuilder extra = new StringBuilder("\n[accent]§ Stats:");
                
                if (block instanceof PowerGenerator) {
                    PowerGenerator gen = (PowerGenerator)block;
                    extra.append("\n  Power: ").append((int)(gen.powerProduction * 60f)).append("/s");
                }
                if (block.hasItems && block.itemCapacity > 0) {
                    extra.append("\n  Items: ").append(block.itemCapacity);
                }
                if (block.hasLiquids && block.liquidCapacity > 0) {
                    extra.append("\n  Liquid: ").append(Strings.autoFixed(block.liquidCapacity, 1));
                }
                if (block.health > 0) {
                    extra.append("\n  HP: ").append((int)block.health);
                }
                
                block.description += extra.toString();
            }
        }

        for (UnitType unit : Vars.content.units()) {
            if (unit.description != null && !unit.description.contains("§")) {
                StringBuilder extra = new StringBuilder("\n[accent]§ Stats:");
                extra.append("\n  HP: ").append((int)unit.health);
                extra.append("\n  Speed: ").append(Strings.autoFixed(unit.speed * 60f, 1));
                if (unit.armor > 0) extra.append("\n  Armor: ").append((int)unit.armor);
                if (unit.mineSpeed > 0) extra.append("\n  Mine: ").append(Strings.autoFixed(unit.mineSpeed, 1));
                if (unit.buildSpeed > 0) extra.append("\n  Build: ").append(Strings.autoFixed(unit.buildSpeed, 1));
                
                unit.description += extra.toString();
            }
        }
    }

    String formatNumber(float num) {
        if (num >= 1000000) {
            return Strings.autoFixed(num / 1000000f, 1) + "M";
        } else if (num >= 1000) {
            return Strings.autoFixed(num / 1000f, 1) + "K";
        }
        return Strings.autoFixed(num, 1);
    }

    String getPercentColor(float percent) {
        if (percent > 75f) return successColor;
        if (percent > 50f) return "[yellow]";
        if (percent > 25f) return "[orange]";
        return warningColor;
    }

    String makeProgressBar(float current, float max, int width) {
        int filled = (int)((current / max) * width);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("]");
        return bar.toString();
    }void addSettingsUI(){
    Vars.ui.settings.addCategory("Tooltips+", table -> {
        table.check("Enable Tooltips", enabled, v -> { enabled = v; saveSettings(); });
        table.row();
        table.check("Show power details", showPowerDetails, v -> { showPowerDetails = v; saveSettings(); });
        table.check("Show item flow", showItemFlow, v -> { showItemFlow = v; saveSettings(); });
        table.row();
        table.check("Show advanced unit info", showUnitAdvanced, v -> { showUnitAdvanced = v; saveSettings(); });
        table.check("Compact mode", compactMode, v -> { compactMode = v; saveSettings(); });
        table.row();
        table.check("Show warnings", showWarnings, v -> { showWarnings = v; saveSettings(); });
        table.check("Play hover sound", playHoverSound, v -> { playHoverSound = v; saveSettings(); });
        table.row();
        table.check("Turret info", showTurretInfo, v -> { showTurretInfo = v; saveSettings(); });
        table.check("Connection info", showConnectionInfo, v -> { showConnectionInfo = v; saveSettings(); });
        table.row();
        table.check("Drill info", showDrillInfo, v -> { showDrillInfo = v; saveSettings(); });
        table.check("Team stats", showTeamStats, v -> { showTeamStats = v; saveSettings(); });
        table.row();
        table.check("Repair info", showRepairInfo, v -> { showRepairInfo = v; saveSettings(); });
        table.check("Storage breakdown", showStorageBreakdown, v -> { showStorageBreakdown = v; saveSettings(); });
        table.row();
        table.check("Follow cursor", followCursor, v -> { followCursor = v; saveSettings(); });
        table.check("Show icons in tooltip", showIcons, v -> { showIcons = v; saveSettings(); });
        table.row();
        table.add("Tooltip opacity: " + tooltipOpacity).left();
        table.button(" + ", () -> { tooltipOpacity = Math.min(10, tooltipOpacity + 1); saveSettings(); }).size(46f, 28f).padLeft(6f);
        table.button(" - ", () -> { tooltipOpacity = Math.max(0, tooltipOpacity - 1); saveSettings(); }).size(46f, 28f).padLeft(4f);
        table.row();
        table.add("Font size: " + fontSize).left();
        table.button(" + ", () -> { fontSize = Math.min(4, fontSize + 1); saveSettings(); }).size(46f, 28f).padLeft(6f);
        table.button(" - ", () -> { fontSize = Math.max(0, fontSize - 1); saveSettings(); }).size(46f, 28f).padLeft(4f);
        table.row();
        table.add("Theme: " + colorTheme).left();
        table.button("Cycle Theme", () -> {
            switch(colorTheme){
                case "default": colorTheme = "dark"; break;
                case "dark": colorTheme = "neon"; break;
                case "neon": colorTheme = "minimal"; break;
                default: colorTheme = "default"; break;
            }
            applyColorTheme();
            saveSettings();
        }).padLeft(8f);
        table.row();
        table.check("Show on minimap", showOnMinimap, v -> { showOnMinimap = v; saveSettings(); });
        table.row();
        table.button("Reset Tooltips+ Defaults", () -> {
            enabled = true;
            showPowerDetails = true;
            showItemFlow = true;
            showUnitAdvanced = true;
            compactMode = false;
            showWarnings = true;
            showTurretInfo = true;
            showConnectionInfo = true;
            showDrillInfo = true;
            showTeamStats = true;
            showRepairInfo = true;
            showStorageBreakdown = true;
            showProductionHistory = true;
            tooltipOpacity = 8;
            followCursor = true;
            showIcons = true;
            fontSize = 1;
            colorTheme = "default";
            playHoverSound = false;
            highlightHovered = true;
            showOnMinimap = false;
            maxTooltipLines = 20;
            applyColorTheme();
            saveSettings();
        }).padTop(6f).row();
        table.add("[lightgray]Tip: Use separate browser/profile to avoid account linking (client-side only).").left().padTop(6f).row();
    });
}void safeLog(String msg){
    try{
        Log.info("[Tooltips+] " + msg);
    }catch(Throwable t){}
}
} // end of TooltipsPlusMod