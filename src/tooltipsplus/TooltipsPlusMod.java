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

    // ===== CORE SETTINGS =====
    boolean enabled = true;
    boolean showPowerDetails = true;
    boolean showItemFlow = true;
    boolean showUnitAdvanced = true;
    boolean compactMode = false;
    boolean showWarnings = true;
    
    // ===== NEW FEATURE TOGGLES =====
    boolean showTurretInfo = true;
    boolean showConnectionInfo = true;
    boolean showDrillInfo = true;
    boolean showTeamStats = true;
    boolean showRepairInfo = true;
    boolean showStorageBreakdown = true;
    boolean showProductionHistory = true;
    boolean comparisonMode = false;
    
    // ===== DISPLAY SETTINGS =====
    int tooltipOpacity = 8; // 0-10 scale
    boolean followCursor = true;
    boolean showIcons = true;
    int fontSize = 1; // 0=small, 1=normal, 2=large
    String colorTheme = "default"; // default, dark, neon, minimal
    boolean playHoverSound = false;
    boolean highlightHovered = true;
    boolean showOnMinimap = false;
    int maxTooltipLines = 20;
    
    // ===== STATE TRACKING =====
    Table tooltipTable;
    Building lastHoveredBuilding;
    Unit lastHoveredUnit;
    float hoverTimer = 0f;
    float hoverDelay = 0.15f;
    
    // ===== COMPARISON MODE =====
    Building pinnedBuilding = null;
    boolean isPinned = false;
    
    // ===== PRODUCTION TRACKING =====
    float[] productionHistory = new float[60];
    int historyIndex = 0;
    float lastHealth = 0f;
    
    // ===== PERFORMANCE =====
    Interval updateInterval = new Interval(2);
    float[] buildingStats = new float[10];
    
    // ===== COLOR THEMES =====
    String statColor = "[stat]";
    String accentColor = "[accent]";
    String warningColor = "[scarlet]";
    String successColor = "[lime]";
    String infoColor = "[lightgray]";
    
    public TooltipsPlusMod() {
        loadSettings();
    }

    void loadSettings() {
        // Core settings
        enabled = arc.Core.settings.getBool("ttp-enabled", true);
        showPowerDetails = arc.Core.settings.getBool("ttp-power", true);
        showItemFlow = arc.Core.settings.getBool("ttp-itemflow", true);
        showUnitAdvanced = arc.Core.settings.getBool("ttp-unitadv", true);
        compactMode = arc.Core.settings.getBool("ttp-compact", false);
        showWarnings = arc.Core.settings.getBool("ttp-warnings", true);
        
        // New features
        showTurretInfo = arc.Core.settings.getBool("ttp-turret", true);
        showConnectionInfo = arc.Core.settings.getBool("ttp-connections", true);
        showDrillInfo = arc.Core.settings.getBool("ttp-drill", true);
        showTeamStats = arc.Core.settings.getBool("ttp-team", true);
        showRepairInfo = arc.Core.settings.getBool("ttp-repair", true);
        showStorageBreakdown = arc.Core.settings.getBool("ttp-storage", true);
        showProductionHistory = arc.Core.settings.getBool("ttp-history", true);
        
        // Display
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
        // Core
        arc.Core.settings.put("ttp-enabled", enabled);
        arc.Core.settings.put("ttp-power", showPowerDetails);
        arc.Core.settings.put("ttp-itemflow", showItemFlow);
        arc.Core.settings.put("ttp-unitadv", showUnitAdvanced);
        arc.Core.settings.put("ttp-compact", compactMode);
        arc.Core.settings.put("ttp-warnings", showWarnings);
        
        // New features
        arc.Core.settings.put("ttp-turret", showTurretInfo);
        arc.Core.settings.put("ttp-connections", showConnectionInfo);
        arc.Core.settings.put("ttp-drill", showDrillInfo);
        arc.Core.settings.put("ttp-team", showTeamStats);
        arc.Core.settings.put("ttp-repair", showRepairInfo);
        arc.Core.settings.put("ttp-storage", showStorageBreakdown);
        arc.Core.settings.put("ttp-history", showProductionHistory);
        
        // Display
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
            default: // "default"
                statColor = "[stat]";
                accentColor = "[accent]";
                warningColor = "[scarlet]";
                successColor = "[lime]";
                infoColor = "[lightgray]";
                break;
        }
    }

    void setupHotkeys() {
        // Toggle tooltips with 'T' key
        Events.run(EventType.Trigger.update, () -> {
            if (arc.Core.input.keyTap(arc.input.KeyCode.t)) {
                enabled = !enabled;
                saveSettings();
                Vars.ui.hudGroup.fill(t -> {
                    t.label(() -> enabled ? "[lime]Tooltips ON" : "[scarlet]Tooltips OFF")
                     .pad(10f);
                }).touchable(arc.scene.ui.Touchable.disabled);
                Time.run(120f, () -> Vars.ui.hudGroup.clear());
            }
            
            // Pin/unpin with 'P' key
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
    }

    void setupTooltipSystem() {
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
            
            // Update production history tracking
            if (updateInterval.get(0, 60f)) {
                updateProductionTracking();
            }
            
            Vec2 mousePos = arc.Core.input.mouseWorld();
            
            // Building detection
            Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
            Building hoveredBuilding = (hoverTile != null) ? hoverTile.build : null;
            
            // Unit detection
            Unit hoveredUnit = Groups.unit.find(u -> {
                return u.within(mousePos.x, mousePos.y, u.hitSize / 2f);
            });
            
            // Handle pinned mode
            if (isPinned && pinnedBuilding != null) {
                showBuildingTooltip(pinnedBuilding);
                return;
            }
            
            // Handle hover state changes
            if (hoveredBuilding != lastHoveredBuilding || hoveredUnit != lastHoveredUnit) {
                hoverTimer = 0f;
                lastHoveredBuilding = hoveredBuilding;
                lastHoveredUnit = hoveredUnit;
                
                if (playHoverSound && (hoveredBuilding != null || hoveredUnit != null)) {
                    Sounds.click.play(0.3f);
                }
            }
            
            // Accumulate hover time
            if (hoveredBuilding != null || hoveredUnit != null) {
                hoverTimer += Time.delta / 60f;
            }
            
            // Highlight hovered building
            if (highlightHovered && hoveredBuilding != null) {
                Lines.stroke(2f);
                Draw.color(arc.graphics.Color.cyan, 0.6f);
                Lines.rect(hoveredBuilding.x - hoveredBuilding.block.size * 4f, 
                          hoveredBuilding.y - hoveredBuilding.block.size * 4f,
                          hoveredBuilding.block.size * 8f,
                          hoveredBuilding.block.size * 8f);
                Draw.reset();
            }
            
            // Show tooltip after delay
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
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    // Continued in Part 2...
}// PART 2/4 - Building Tooltip Display Methods
// Add these methods to the TooltipsPlusMod class

void showBuildingTooltip(Building build) {
    tooltipTable.clear();
    tooltipTable.visible = true;
    
    int lineCount = 0;
    
    // Title with icon
    Table titleRow = new Table();
    if (showIcons && build.block.fullIcon != null) {
        titleRow.image(build.block.fullIcon).size(24f * (fontSize + 1)).padRight(4f);
    }
    titleRow.add(accentColor + build.block.localizedName).style(Styles.outlineLabel);
    if (isPinned && build == pinnedBuilding) {
        titleRow.add(" [royal]📌").style(Styles.outlineLabel);
    }
    tooltipTable.add(titleRow).left().row();
    lineCount++;
    
    if (!compactMode) {
        tooltipTable.add(infoColor + repeat("─", 20)).padTop(2f).padBottom(2f).row();
        lineCount++;
    }
    
    // === TURRET INFO ===
    if (showTurretInfo && build.block instanceof Turret) {
        addTurretInfo(build);
        lineCount += 4;
    }
    
    // === DRILL INFO ===
    if (showDrillInfo && build.block instanceof Drill) {
        addDrillInfo(build);
        lineCount += 3;
    }
    
    // === POWER SECTION ===
    if (showPowerDetails && build.power != null) {
        addPowerInfo(build);
        lineCount += 3;
    }
    
    // === ITEM FLOW SECTION ===
    if (showItemFlow && build.block.hasItems) {
        addItemFlowInfo(build);
        lineCount += 2;
    }
    
    // === STORAGE BREAKDOWN ===
    if (showStorageBreakdown && build.block.hasItems && build.items != null) {
        addStorageBreakdown(build);
        lineCount += 4;
    }
    
    // === LIQUID SECTION ===
    if (build.block.hasLiquids && build.block.liquidCapacity > 0) {
        addLiquidInfo(build);
        lineCount += 2;
    }
    
    // === PRODUCTION INFO ===
    if (build.block instanceof GenericCrafter) {
        addProductionInfo(build);
        lineCount += 2;
        
        if (showProductionHistory) {
            addProductionHistory(build);
            lineCount += 2;
        }
    }
    
    // === CONVEYOR/CONDUIT FLOW ===
    if (showItemFlow) {
        addConveyorFlowInfo(build);
        lineCount += 1;
    }
    
    // === CONNECTION INFO ===
    if (showConnectionInfo) {
        addConnectionInfo(build);
        lineCount += 3;
    }
    
    // === HEALTH & WARNINGS ===
    addHealthInfo(build);
    lineCount += 1;
    
    // === REPAIR INFO ===
    if (showRepairInfo) {
        addRepairInfo(build);
        lineCount += 1;
    }
    
    // === TEAM STATS ===
    if (showTeamStats) {
        addTeamInfo(build);
        lineCount += 2;
    }
    
    // === POWER NETWORK ===
    if (showPowerDetails && build.power != null) {
        addPowerNetworkInfo(build);
        lineCount += 5;
    }
    
    // Position tooltip
    positionTooltip();
    tooltipTable.pack();
}

void addTurretInfo(Building build) {
    if (build.block instanceof Turret) {
        Turret turret = (Turret)build.block;
        String icon = showIcons ? "🎯" : "";
        
        tooltipTable.add(statColor + "─ Turret ─").padTop(4f).row();
        
        // Ammo status
        if (build.items != null && turret.hasItems) {
            int ammo = build.items.total();
            String ammoColor = ammo > turret.itemCapacity * 0.5f ? successColor : 
                              ammo > 0 ? "[yellow]" : warningColor;
            tooltipTable.add(icon + statColor + "Ammo: " + ammoColor + ammo + infoColor + "/" + turret.itemCapacity).left().row();
        }
        
        // Targeting status
        if (build instanceof Turret.TurretBuild) {
            Turret.TurretBuild tb = (Turret.TurretBuild)build;
            if (tb.target != null) {
                tooltipTable.add("  " + warningColor + "⚔ Locked On").left().row();
            } else {
                tooltipTable.add("  " + infoColor + "⚬ Idle").left().row();
            }
            
            // Reload status
            float reloadPercent = (tb.reload / turret.reload) * 100f;
            tooltipTable.add("  " + statColor + "Reload: " + makeProgressBar(tb.reload, turret.reload, 10) + " " + (int)reloadPercent + "%").left().row();
        }
        
        // Range
        float rangeTiles = turret.range / 8f;
        tooltipTable.add("  " + infoColor + "Range: " + accentColor + Strings.autoFixed(rangeTiles, 1) + infoColor + " tiles").left().row();
    }
}

void addDrillInfo(Building build) {
    if (build.block instanceof Drill) {
        Drill drill = (Drill)build.block;
        Tile tile = build.tile;
        Item drops = tile.drop();
        
        tooltipTable.add(statColor + "─ Drill ─").padTop(4f).row();
        
        if (drops != null) {
            tooltipTable.add("⛏" + statColor + "Mining: " + accentColor + drops.emoji() + " " + drops.localizedName).left().row();
            
            // Calculate extraction rate
            float drillTime = drill.getDrillTime(drops);
            float itemsPerSec = 60f / drillTime;
            tooltipTable.add("  " + infoColor + "Rate: " + accentColor + Strings.autoFixed(itemsPerSec, 2) + "/s").left().row();
            
            // Boost status
            if (build.liquids != null && build.liquids.current() != null) {
                float boost = drill.liquidBoostIntensity;
                tooltipTable.add("  [royal]💧 Boosted +" + (int)(boost * 100) + "%").left().row();
            }
        } else {
            tooltipTable.add(warningColor + "⚠ No ore here!").left().row();
        }
    }
}

void addPowerInfo(Building build) {
    if (build.block instanceof PowerGenerator) {
        PowerGenerator gen = (PowerGenerator)build.block;
        float production = gen.powerProduction * 60f;
        String icon = showIcons ? Icon.power.toString() : "";
        tooltipTable.add(icon + statColor + "Power: " + accentColor + "+" + Strings.autoFixed(production, 1) + "/s").left().row();
        
        if (build.power != null && production > 0) {
            float efficiency = build.power.status * 100f;
            String color = efficiency > 90f ? successColor : efficiency > 50f ? "[yellow]" : warningColor;
            tooltipTable.add("  " + infoColor + "Efficiency: " + color + (int)efficiency + "%").left().row();
        }
    } else if (build.power != null && build.block.consumesPower) {
        float consumption = build.block.consPower.usage * 60f;
        String icon = showIcons ? Icon.power.toString() : "";
        tooltipTable.add(icon + statColor + "Uses: " + accentColor + Strings.autoFixed(consumption, 1) + "/s").left().row();
        
        if (build.power.status < 1f) {
            tooltipTable.add("  " + warningColor + "⚠ Low Power (" + (int)(build.power.status * 100) + "%)").left().row();
        }
    }
}

void addItemFlowInfo(Building build) {
    if (build.block.itemCapacity > 0) {
        int total = build.items != null ? build.items.total() : 0;
        int cap = build.block.itemCapacity;
        String icon = showIcons ? Icon.box.toString() : "";
        
        float fillPercent = (total / (float)cap) * 100f;
        String fillColor = fillPercent > 90f ? warningColor : 
                          fillPercent > 70f ? "[yellow]" : accentColor;
        
        tooltipTable.add(icon + statColor + "Items: " + fillColor + total + infoColor + "/" + cap).left().row();
        
        if (build.items != null && total > 0) {
            Item dominant = build.items.first();
            if (dominant != null) {
                int count = build.items.get(dominant);
                tooltipTable.add("  " + infoColor + dominant.emoji() + " " + dominant.localizedName + ": " + accentColor + count).left().row();
            }
        }
        
        if (showWarnings && total >= cap) {
            tooltipTable.add("  [yellow]⚠ Storage Full").left().row();
        }
    }
}

void addStorageBreakdown(Building build) {
    if (build.items != null && build.items.total() > 5) {
        tooltipTable.add(statColor + "─ Contents ─").padTop(4f).row();
        
        int shown = 0;
        for (Item item : Vars.content.items()) {
            int amount = build.items.get(item);
            if (amount > 0 && shown < 3) {
                float percent = (amount / (float)build.items.total()) * 100f;
                tooltipTable.add("  " + item.emoji() + " " + accentColor + amount + infoColor + " (" + (int)percent + "%)").left().row();
                shown++;
            }
        }
        
        if (build.items.total() > shown) {
            tooltipTable.add("  " + infoColor + "... +" + (build.items.total() - shown) + " more").left().row();
        }
    }
}

void addLiquidInfo(Building build) {
    if (build.liquids != null) {
        Liquid current = build.liquids.current();
        if (current != null) {
            float amount = build.liquids.get(current);
            if (amount > 0.01f) {
                String icon = showIcons ? Icon.liquid.toString() : "";
                float cap = build.block.liquidCapacity;
                float percent = (amount / cap) * 100f;
                
                String fillColor = percent > 90f ? warningColor : accentColor;
                
                tooltipTable.add(icon + statColor + "Liquid: " + fillColor + Strings.autoFixed(amount, 1) + infoColor + "/" + Strings.autoFixed(cap, 1)).left().row();
                tooltipTable.add("  " + infoColor + current.emoji() + " " + current.localizedName).left().row();
            }
        }
    }
}

void addProductionInfo(Building build) {
    if (build.power != null) {
        float eff = build.power.status * 100f;
        String color = eff > 90f ? successColor : eff > 50f ? "[yellow]" : warningColor;
        tooltipTable.add(statColor + "Production: " + color + (int)eff + "%").left().row();
    }
}

void addProductionHistory(Building build) {
    float avg = 0f;
    for (float v : productionHistory) avg += v;
    avg /= 60f;
    
    String avgColor = avg > 0.9f ? successColor : avg > 0.5f ? "[yellow]" : warningColor;
    tooltipTable.add("  " + infoColor + "Avg 1min: " + avgColor + (int)(avg * 100) + "%").left().row();
}

void addHealthInfo(Building build) {
    float healthPercent = (build.health / build.maxHealth) * 100f;
    String healthColor = healthPercent > 75f ? successColor : healthPercent > 40f ? "[yellow]" : warningColor;
    String icon = showIcons ? Icon.defense.toString() : "";
    
    tooltipTable.add(icon + statColor + "HP: " + healthColor + (int)build.health + infoColor + "/" + (int)build.maxHealth).left().row();
    
    if (showWarnings && healthPercent < 30f) {
        tooltipTable.add("  " + warningColor + "⚠ Critical Damage!").left().row();
    }
}

void addRepairInfo(Building build) {
    float healthDelta = build.health - lastHealth;
    lastHealth = build.health;
    
    if (healthDelta > 0.1f) {
        tooltipTable.add("  " + successColor + "🔧 Repairing +" + Strings.autoFixed(healthDelta * 60f, 1) + " HP/s").left().row();
    } else if (healthDelta < -0.1f) {
        tooltipTable.add("  " + warningColor + "💥 Taking Damage " + Strings.autoFixed(Math.abs(healthDelta * 60f), 1) + " HP/s").left().row();
    }
}

// Continued in Part 3...// PART 3/4 - Unit Tooltips & Network Analysis
// Add these methods to the TooltipsPlusMod class

void addTeamInfo(Building build) {
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
        if (nearby.block.acceptsItems() || nearby.block instanceof Conveyor) {
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
    
    // Title with icon
    Table titleRow = new Table();
    if (showIcons && unit.type.fullIcon != null) {
        titleRow.image(unit.type.fullIcon).size(24f * (fontSize + 1)).padRight(4f);
    }
    titleRow.add(accentColor + unit.type.localizedName).style(Styles.outlineLabel);
    tooltipTable.add(titleRow).left().row();
    
    if (!compactMode) {
        tooltipTable.add(infoColor + repeat("─", 20)).padTop(2f).padBottom(2f).row();
    }
    
    // === HEALTH & ARMOR ===
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
    
    // === MOVEMENT ===
    if (showUnitAdvanced) {
        String moveIcon = unit.type.flying ? "✈" : "⛏";
        tooltipTable.add(moveIcon + statColor + "Speed: " + accentColor + Strings.autoFixed(unit.type.speed * 60f, 1)).left().row();
        
        if (unit.type.flying) {
            tooltipTable.add("  [sky]Flying Unit").left().row();
        }
    }
    
    // === ABILITIES ===
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
    
    // === COMBAT STATS ===
    if (showUnitAdvanced && unit.type.weapons.size > 0) {
        addWeaponInfo(unit);
    }
    
    // === CURRENT STATUS ===
    addUnitStatusInfo(unit);
    
    // === OWNER INFO ===
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
}

// Continued in Part 4...// PART 4/4 - Settings UI & Presets
// Add these methods to complete the TooltipsPlusMod class

void addSettingsUI() {
    try {
        Vars.ui.settings.addCategory("Tooltips+", Icon.book, table -> {
            table.defaults().left().padTop(4f);
            
            // === MAIN TOGGLE ===
            table.add("[accent]" + repeat("═", 15) + " Main " + repeat("═", 15)).center().colspan(2).padBottom(8f).row();
            
            table.check("Enable Tooltips+", enabled, v -> {
                enabled = v;
                saveSettings();
                if (v) {
                    setupTooltipSystem();
                    Vars.ui.showInfo("[lime]Tooltips+ Enabled");
                } else {
                    tooltipTable.visible = false;
                    Vars.ui.showInfo("[scarlet]Tooltips+ Disabled");
                }
            }).colspan(2).left().row();
            
            table.add("[lightgray]Hotkeys: T = Toggle | P = Pin").colspan(2).left().padTop(4f).row();
            
            // === DISPLAY SETTINGS ===
            table.add("[accent]" + repeat("═", 14) + " Display " + repeat("═", 14)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            table.check("Compact Mode", compactMode, v -> {
                compactMode = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Follow Cursor", followCursor, v -> {
                followCursor = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Show Icons", showIcons, v -> {
                showIcons = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Highlight Hovered", highlightHovered, v -> {
                highlightHovered = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Hover Sound", playHoverSound, v -> {
                playHoverSound = v;
                saveSettings();
            }).colspan(2).left().row();
            
            // Color Theme
            table.add("Color Theme: ").left();
            table.button(colorTheme, () -> {
                int index = 0;
                String[] themes = {"default", "dark", "neon", "minimal"};
                for (int i = 0; i < themes.length; i++) {
                    if (themes[i].equals(colorTheme)) {
                        index = (i + 1) % themes.length;
                        break;
                    }
                }
                colorTheme = themes[index];
                applyColorTheme();
                saveSettings();
            }).width(150f).row();
            
            // Opacity slider
            table.add("Tooltip Opacity: ").left();
            table.slider(0, 10, 1, tooltipOpacity, v -> {
                tooltipOpacity = (int)v;
                saveSettings();
            }).width(200f).row();
            
            // Hover delay slider
            table.add("Hover Delay: ").left();
            table.slider(0f, 1f, 0.05f, hoverDelay, v -> {
                hoverDelay = v;
                saveSettings();
            }).width(200f).row();
            
            table.add("[lightgray](" + Strings.autoFixed(hoverDelay, 2) + "s)").colspan(2).left().padTop(-4f).row();
            
            // Font size
            table.add("Font Size: ").left();
            table.slider(0, 2, 1, fontSize, v -> {
                fontSize = (int)v;
                saveSettings();
            }).width(200f).row();
            
            String[] fontLabels = {"Small", "Normal", "Large"};
            table.add("[lightgray]" + fontLabels[fontSize]).colspan(2).left().padTop(-4f).row();
            
            // === FEATURE TOGGLES ===
            table.add("[accent]" + repeat("═", 13) + " Features " + repeat("═", 13)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            table.check("Power Details", showPowerDetails, v -> {
                showPowerDetails = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Item Flow Rates", showItemFlow, v -> {
                showItemFlow = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Advanced Unit Info", showUnitAdvanced, v -> {
                showUnitAdvanced = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Show Warnings", showWarnings, v -> {
                showWarnings = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Turret Analytics", showTurretInfo, v -> {
                showTurretInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Drill Analytics", showDrillInfo, v -> {
                showDrillInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Connection Info", showConnectionInfo, v -> {
                showConnectionInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Storage Breakdown", showStorageBreakdown, v -> {
                showStorageBreakdown = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Production History", showProductionHistory, v -> {
                showProductionHistory = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Repair Indicators", showRepairInfo, v -> {
                showRepairInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            table.check("Team Stats (PvP)", showTeamStats, v -> {
                showTeamStats = v;
                saveSettings();
            }).colspan(2).left().row();
            
            // === PRESET BUTTONS ===
            table.add("[accent]" + repeat("═", 12) + " Presets " + repeat("═", 12)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            Table presetRow1 = new Table();
            presetRow1.button("Minimal", Icon.zoom, () -> {
                applyPreset("minimal");
                Vars.ui.showInfo("[lime]Applied Minimal preset");
            }).size(140f, 50f).pad(4f);
            
            presetRow1.button("Balanced", Icon.settings, () -> {
                applyPreset("balanced");
                Vars.ui.showInfo("[lime]Applied Balanced preset");
            }).size(140f, 50f).pad(4f);
            
            table.add(presetRow1).colspan(2).center().padTop(8f).row();
            
            Table presetRow2 = new Table();
            presetRow2.button("Maximum", Icon.zoom, () -> {
                applyPreset("maximum");
                Vars.ui.showInfo("[lime]Applied Maximum preset");
            }).size(140f, 50f).pad(4f);
            
            presetRow2.button("Combat", Icon.units, () -> {
                applyPreset("combat");
                Vars.ui.showInfo("[lime]Applied Combat preset");
            }).size(140f, 50f).pad(4f);
            
            table.add(presetRow2).colspan(2).center().padTop(4f).row();
            
            // === INFO & HELP ===
            table.add("[accent]" + repeat("═", 15) + " Info " + repeat("═", 15)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            table.add("[lightgray]Hover over buildings and units\nto see detailed tooltips").colspan(2).center().padTop(8f).row();
            
            table.add("[sky]Version 3.0 - Full Featured").colspan(2).center().padTop(8f).row();
            
            // === RESET BUTTON ===
            table.button("Reset to Defaults", Icon.refresh, () -> {
                resetToDefaults();
                Vars.ui.showInfo("[lime]Reset to defaults");
            }).size(200f, 50f).colspan(2).center().padTop(16f);
        });
    } catch (Throwable ex) {
        Log.err("TooltipsPlus: Failed to add settings UI", ex);
    }
}

void applyPreset(String preset) {
    switch (preset) {
        case "minimal":
            enabled = true;
            compactMode = true;
            showPowerDetails = false;
            showItemFlow = false;
            showUnitAdvanced = false;
            showWarnings = false;
            showIcons = false;
            showTurretInfo = false;
            showConnectionInfo = false;
            showDrillInfo = false;
            showTeamStats = false;
            showRepairInfo = false;
            showStorageBreakdown = false;
            showProductionHistory = false;
            tooltipOpacity = 6;
            hoverDelay = 0.3f;
            colorTheme = "minimal";
            fontSize = 0;
            break;
            
        case "balanced":
            enabled = true;
            compactMode = false;
            showPowerDetails = true;
            showItemFlow = true;
            showUnitAdvanced = true;
            showWarnings = true;
            showIcons = true;
            showTurretInfo = true;
            showConnectionInfo = true;
            showDrillInfo = true;
            showTeamStats = true;
            showRepairInfo = true;
            showStorageBreakdown = true;
            showProductionHistory = false;
            tooltipOpacity = 8;
            hoverDelay = 0.15f;
            colorTheme = "default";
            fontSize = 1;
            break;
            
        case "maximum":
            enabled = true;
            compactMode = false;
            showPowerDetails = true;
            showItemFlow = true;
            showUnitAdvanced = true;
            showWarnings = true;
            showIcons = true;
            showTurretInfo = true;
            showConnectionInfo = true;
            showDrillInfo = true;
            showTeamStats = true;
            showRepairInfo = true;
            showStorageBreakdown = true;
            showProductionHistory = true;
            tooltipOpacity = 9;
            hoverDelay = 0.05f;
            colorTheme = "default";
            fontSize = 1;
            highlightHovered = true;
            break;
            
        case "combat":
            enabled = true;
            compactMode = true;
            showPowerDetails = false;
            showItemFlow = false;
            showUnitAdvanced = true;
            showWarnings = true;
            showIcons = true;
            showTurretInfo = true;
            showConnectionInfo = false;
            showDrillInfo = false;
            showTeamStats = true;
            showRepairInfo = true;
            showStorageBreakdown = false;
            showProductionHistory = false;
            tooltipOpacity = 7;
            hoverDelay = 0.1f;
            colorTheme = "neon";
            fontSize = 1;
            break;
    }
    applyColorTheme();
    saveSettings();
}

void resetToDefaults() {
    enabled = true;
    compactMode = false;
    showPowerDetails = true;
    showItemFlow = true;
    showUnitAdvanced = true;
    showWarnings = true;
    showIcons = true;
    showTurretInfo = true;
    showConnectionInfo = true;
    showDrillInfo = true;
    showTeamStats = true;
    showRepairInfo = true;
    showStorageBreakdown = true;
    showProductionHistory = true;
    tooltipOpacity = 8;
    followCursor = true;
    hoverDelay = 0.15f;
    fontSize = 1;
    colorTheme = "default";
    playHoverSound = false;
    highlightHovered = true;
    showOnMinimap = false;
    maxTooltipLines = 20;
    applyColorTheme();
    saveSettings();
}

// === END OF TOOLTIPSPLUS MOD ===
}