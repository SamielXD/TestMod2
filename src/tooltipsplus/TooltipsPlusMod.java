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
import mindustry.world.meta.*;

public class TooltipsPlusMod extends Mod {

    // ===== SETTINGS =====
    boolean enabled = true;
    boolean showPowerDetails = true;
    boolean showItemFlow = true;
    boolean showUnitAdvanced = true;
    boolean compactMode = false;
    boolean showWarnings = true;
    
    // Display settings
    int tooltipOpacity = 8; // 0-10 scale
    boolean followCursor = true;
    boolean showIcons = true;
    
    // ===== STATE =====
    Table tooltipTable;
    Building lastHoveredBuilding;
    Unit lastHoveredUnit;
    float hoverTimer = 0f;
    float hoverDelay = 0.15f; // seconds before tooltip shows
    
    // Performance tracking
    Interval updateInterval = new Interval(2);
    float[] buildingStats = new float[10]; // reusable array for calculations
    
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
        tooltipOpacity = arc.Core.settings.getInt("ttp-opacity", 8);
        followCursor = arc.Core.settings.getBool("ttp-follow", true);
        showIcons = arc.Core.settings.getBool("ttp-icons", true);
    }

    void saveSettings() {
        arc.Core.settings.put("ttp-enabled", enabled);
        arc.Core.settings.put("ttp-power", showPowerDetails);
        arc.Core.settings.put("ttp-itemflow", showItemFlow);
        arc.Core.settings.put("ttp-unitadv", showUnitAdvanced);
        arc.Core.settings.put("ttp-compact", compactMode);
        arc.Core.settings.put("ttp-warnings", showWarnings);
        arc.Core.settings.put("ttp-opacity", tooltipOpacity);
        arc.Core.settings.put("ttp-follow", followCursor);
        arc.Core.settings.put("ttp-icons", showIcons);
        arc.Core.settings.forceSave();
    }

    @Override
    public void init() {
        Log.info("TooltipsPlus v2.0 initializing...");
        if (enabled) {
            setupTooltipSystem();
            injectStaticDescriptions();
        }
        addSettingsUI();
        Log.info("TooltipsPlus loaded successfully");
    }

    void setupTooltipSystem() {
        // Create main tooltip table with dynamic opacity
        tooltipTable = new Table(Styles.black);
        tooltipTable.background(Tex.buttonEdge3);
        tooltipTable.margin(6f);
        tooltipTable.visible = false;
        Vars.ui.hudGroup.addChild(tooltipTable);
        
        // Main hover detection loop
        Events.run(EventType.Trigger.draw, () -> {
            if (!enabled || Vars.state.isMenu()) {
                tooltipTable.visible = false;
                hoverTimer = 0f;
                return;
            }
            
            Vec2 mousePos = arc.Core.input.mouseWorld();
            
            // Building detection
            Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
            Building hoveredBuilding = (hoverTile != null) ? hoverTile.build : null;
            
            // Unit detection
            Unit hoveredUnit = Groups.unit.find(u -> {
                return u.within(mousePos.x, mousePos.y, u.hitSize / 2f);
            });
            
            // Handle hover state changes
            if (hoveredBuilding != lastHoveredBuilding || hoveredUnit != lastHoveredUnit) {
                hoverTimer = 0f;
                lastHoveredBuilding = hoveredBuilding;
                lastHoveredUnit = hoveredUnit;
            }
            
            // Accumulate hover time
            if (hoveredBuilding != null || hoveredUnit != null) {
                hoverTimer += Time.delta / 60f;
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
            
            // Hide tooltip
            tooltipTable.visible = false;
        });
    }

    void showBuildingTooltip(Building build) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        // Title with icon
        Table titleRow = new Table();
        if (showIcons && build.block.fullIcon != null) {
            titleRow.image(build.block.fullIcon).size(24f).padRight(4f);
        }
        titleRow.add("[accent]" + build.block.localizedName).style(Styles.outlineLabel);
        tooltipTable.add(titleRow).left().row();
        
        if (!compactMode) {
            tooltipTable.add("[lightgray]" + "─".repeat(20)).padTop(2f).padBottom(2f).row();
        }
        
        // === POWER SECTION ===
        if (showPowerDetails && build.power != null) {
            addPowerInfo(build);
        }
        
        // === ITEM FLOW SECTION ===
        if (showItemFlow && build.block.hasItems) {
            addItemFlowInfo(build);
        }
        
        // === LIQUID SECTION ===
        if (build.block.hasLiquids && build.block.liquidCapacity > 0) {
            addLiquidInfo(build);
        }
        
        // === PRODUCTION INFO ===
        if (build instanceof GenericCrafter crafter) {
            addProductionInfo(crafter);
        }
        
        // === HEALTH & WARNINGS ===
        addHealthInfo(build);
        
        // Position tooltip
        positionTooltip();
        tooltipTable.pack();
    }

    void addPowerInfo(Building build) {
        if (build.block instanceof PowerGenerator gen) {
            float production = gen.powerProduction * 60f;
            String icon = showIcons ? Icon.power.toString() : "";
            tooltipTable.add(icon + "[stat]Power: [accent]+" + Strings.autoFixed(production, 1) + "/s").left().row();
            
            // Efficiency for generators
            if (build.power != null && production > 0) {
                float efficiency = build.power.status * 100f;
                String color = efficiency > 90f ? "[lime]" : efficiency > 50f ? "[yellow]" : "[scarlet]";
                tooltipTable.add("  [lightgray]Efficiency: " + color + (int)efficiency + "%").left().row();
            }
        } else if (build.power != null && build.block.consumesPower) {
            float consumption = build.block.consPower.usage * 60f;
            String icon = showIcons ? Icon.power.toString() : "";
            tooltipTable.add(icon + "[stat]Uses: [accent]" + Strings.autoFixed(consumption, 1) + "/s").left().row();
            
            // Power satisfaction
            if (build.power.status < 1f) {
                tooltipTable.add("  [scarlet]⚠ Low Power (" + (int)(build.power.status * 100) + "%)").left().row();
            }
        }
    }

    void addItemFlowInfo(Building build) {
        if (build.block.itemCapacity > 0) {
            int total = build.items != null ? build.items.total() : 0;
            int cap = build.block.itemCapacity;
            String icon = showIcons ? Icon.box.toString() : "";
            
            tooltipTable.add(icon + "[stat]Items: [accent]" + total + "/" + cap).left().row();
            
            // Show dominant item if storage > 0
            if (build.items != null && total > 0) {
                Item dominant = build.items.first();
                if (dominant != null) {
                    int count = build.items.get(dominant);
                    tooltipTable.add("  [lightgray]" + dominant.emoji() + " " + dominant.localizedName + ": " + count).left().row();
                }
            }
            
            // Warning if full
            if (showWarnings && total >= cap) {
                tooltipTable.add("  [yellow]⚠ Storage Full").left().row();
            }
        }
    }

    void addLiquidInfo(Building build) {
        if (build.liquids != null && build.liquids.total() > 0.01f) {
            String icon = showIcons ? Icon.liquid.toString() : "";
            float total = build.liquids.total();
            float cap = build.block.liquidCapacity;
            
            tooltipTable.add(icon + "[stat]Liquid: [accent]" + Strings.autoFixed(total, 1) + "/" + Strings.autoFixed(cap, 1)).left().row();
            
            // Show liquid type
            Liquid current = build.liquids.current();
            if (current != null) {
                tooltipTable.add("  [lightgray]" + current.emoji() + " " + current.localizedName).left().row();
            }
        }
    }

    void addProductionInfo(GenericCrafter crafter) {
        if (crafter.efficiency > 0) {
            float eff = crafter.efficiency * 100f;
            String color = eff > 90f ? "[lime]" : eff > 50f ? "[yellow]" : "[scarlet]";
            tooltipTable.add("[stat]Production: " + color + (int)eff + "%").left().row();
        }
    }

    void addHealthInfo(Building build) {
        float healthPercent = (build.health / build.maxHealth) * 100f;
        String healthColor = healthPercent > 75f ? "[lime]" : healthPercent > 40f ? "[yellow]" : "[scarlet]";
        String icon = showIcons ? Icon.defense.toString() : "";
        
        tooltipTable.add(icon + "[stat]HP: " + healthColor + (int)build.health + "[lightgray]/" + (int)build.maxHealth).left().row();
        
        if (showWarnings && healthPercent < 30f) {
            tooltipTable.add("  [scarlet]⚠ Critical Damage!").left().row();
        }
    }void showUnitTooltip(Unit unit) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        // Title with icon
        Table titleRow = new Table();
        if (showIcons && unit.type.fullIcon != null) {
            titleRow.image(unit.type.fullIcon).size(24f).padRight(4f);
        }
        titleRow.add("[accent]" + unit.type.localizedName).style(Styles.outlineLabel);
        tooltipTable.add(titleRow).left().row();
        
        if (!compactMode) {
            tooltipTable.add("[lightgray]" + "─".repeat(20)).padTop(2f).padBottom(2f).row();
        }
        
        // === HEALTH & ARMOR ===
        float healthPercent = (unit.health / unit.maxHealth) * 100f;
        String healthColor = healthPercent > 75f ? "[lime]" : healthPercent > 40f ? "[yellow]" : "[scarlet]";
        String icon = showIcons ? Icon.defense.toString() : "";
        
        tooltipTable.add(icon + "[stat]HP: " + healthColor + (int)unit.health + "[lightgray]/" + (int)unit.maxHealth).left().row();
        
        if (unit.type.armor > 0) {
            tooltipTable.add("  [lightgray]Armor: [accent]" + (int)unit.type.armor).left().row();
        }
        
        if (showWarnings && healthPercent < 25f) {
            tooltipTable.add("  [scarlet]⚠ Critical HP!").left().row();
        }
        
        // === MOVEMENT ===
        if (showUnitAdvanced) {
            String moveIcon = unit.type.flying ? "✈" : "⛏";
            tooltipTable.add(moveIcon + "[stat]Speed: [accent]" + Strings.autoFixed(unit.type.speed * 60f, 1)).left().row();
            
            if (unit.type.flying) {
                tooltipTable.add("  [sky]Flying Unit").left().row();
            }
        }
        
        // === ABILITIES ===
        if (showUnitAdvanced) {
            if (unit.type.mineSpeed > 0) {
                tooltipTable.add("⛏[stat]Mine: [accent]" + Strings.autoFixed(unit.type.mineSpeed, 1) + "/s").left().row();
                
                // Show mining tier
                if (unit.type.mineTier > 0) {
                    tooltipTable.add("  [lightgray]Tier: " + unit.type.mineTier).left().row();
                }
            }
            
            if (unit.type.buildSpeed > 0) {
                tooltipTable.add("🔨[stat]Build: [accent]" + Strings.autoFixed(unit.type.buildSpeed, 1) + "/s").left().row();
            }
            
            if (unit.type.itemCapacity > 0) {
                int carrying = unit.stack != null && unit.stack.item != null ? unit.stack.amount : 0;
                tooltipTable.add("📦[stat]Carry: [accent]" + carrying + "/" + unit.type.itemCapacity).left().row();
                
                // Show what item is being carried
                if (unit.stack != null && unit.stack.item != null && carrying > 0) {
                    tooltipTable.add("  [lightgray]" + unit.stack.item.emoji() + " " + unit.stack.item.localizedName).left().row();
                }
            }
        }
        
        // === COMBAT STATS ===
        if (showUnitAdvanced && unit.type.weapons.size > 0) {
            addWeaponInfo(unit);
        }
        
        // === CURRENT STATUS ===
        addUnitStatusInfo(unit);
        
        // === OWNER INFO (for multiplayer) ===
        if (Vars.state.rules.pvp && unit.team != Vars.player.team()) {
            tooltipTable.add("[scarlet]Enemy Unit").left().row();
        } else if (unit.isPlayer()) {
            tooltipTable.add("[royal]Player Unit").left().row();
        }
        
        // Position tooltip
        positionTooltip();
        tooltipTable.pack();
    }

    void addWeaponInfo(Unit unit) {
        // Calculate total DPS from all weapons
        float totalDPS = 0f;
        for (var weapon : unit.type.weapons) {
            if (weapon.bullet != null) {
                float dps = weapon.bullet.damage * (60f / weapon.reload);
                totalDPS += dps;
            }
        }
        
        if (totalDPS > 0) {
            tooltipTable.add("⚔[stat]DPS: [accent]" + Strings.autoFixed(totalDPS, 1)).left().row();
            
            // Show weapon count if multiple
            if (unit.type.weapons.size > 1) {
                tooltipTable.add("  [lightgray]Weapons: " + unit.type.weapons.size).left().row();
            }
        }
    }

    void addUnitStatusInfo(Unit unit) {
        // Check if unit is doing something
        if (unit.mineTile != null) {
            tooltipTable.add("[stat]⛏ Mining...").left().row();
        } else if (unit.activelyBuilding()) {
            tooltipTable.add("[stat]🔨 Building...").left().row();
        } else if (unit.isShooting) {
            tooltipTable.add("[stat]⚔ Combat").left().row();
        } else if (unit.moving()) {
            tooltipTable.add("[stat]→ Moving").left().row();
        }
        
        // Status effects
        if (unit.statuses.size > 0) {
            tooltipTable.add("[coral]Effects:").left().row();
            for (var effect : unit.statuses) {
                if (effect.effect != null && effect.time > 0) {
                    String effectName = effect.effect.localizedName;
                    tooltipTable.add("  • " + effectName).left().row();
                }
            }
        }
    }

    void positionTooltip() {
        Vec2 screenPos = arc.Core.input.mouse();
        float x = screenPos.x + 20f;
        float y = screenPos.y + 20f;
        
        // Keep tooltip on screen
        if (x + tooltipTable.getWidth() > arc.Core.graphics.getWidth()) {
            x = screenPos.x - tooltipTable.getWidth() - 10f;
        }
        if (y + tooltipTable.getHeight() > arc.Core.graphics.getHeight()) {
            y = screenPos.y - tooltipTable.getHeight() - 10f;
        }
        
        if (followCursor) {
            tooltipTable.setPosition(x, y);
        } else {
            // Fixed position mode (bottom right corner)
            tooltipTable.setPosition(
                arc.Core.graphics.getWidth() - tooltipTable.getWidth() - 20f,
                20f
            );
        }
        
        // Apply opacity
        tooltipTable.color.a = tooltipOpacity / 10f;
    }

    // === CONVEYOR & FLOW TRACKING ===
    // This section adds item throughput detection for conveyors
    
    void addConveyorFlowInfo(Building build) {
        if (build.block instanceof Conveyor conveyor) {
            // Estimate throughput based on conveyor speed
            float itemsPerSec = conveyor.speed * 60f;
            tooltipTable.add("→[stat]Flow: [accent]" + Strings.autoFixed(itemsPerSec, 1) + " items/s").left().row();
        }
        
        if (build.block instanceof Conduit conduit) {
            // Show liquid flow rate
            float liquidPerSec = conduit.liquidCapacity * 60f;
            tooltipTable.add("→[stat]Flow: [accent]" + Strings.autoFixed(liquidPerSec, 1) + " units/s").left().row();
        }
    }

    // === UPGRADE PREVIEW ===
    // Shows what the next tier upgrade would give
    
    void addUpgradePreview(Building build) {
        // Find next tier block (simple heuristic: same name with higher tier number)
        Block nextTier = findNextTier(build.block);
        if (nextTier != null) {
            tooltipTable.add("[gray]─ Upgrade Preview ─").padTop(4f).row();
            tooltipTable.add("[sky]→ " + nextTier.localizedName).left().row();
            
            // Compare stats
            if (nextTier instanceof PowerGenerator nextGen && build.block instanceof PowerGenerator currentGen) {
                float powerIncrease = (nextGen.powerProduction - currentGen.powerProduction) * 60f;
                if (powerIncrease > 0) {
                    tooltipTable.add("  [lime]+↑ " + Strings.autoFixed(powerIncrease, 1) + " power/s").left().row();
                }
            }
            
            // Health increase
            float healthIncrease = nextTier.health - build.block.health;
            if (healthIncrease > 0) {
                tooltipTable.add("  [lime]+↑ " + (int)healthIncrease + " HP").left().row();
            }
        }
    }

    Block findNextTier(Block current) {
        // Simple tier detection: find blocks with similar names
        String baseName = current.name.replaceAll("[0-9]", "");
        int currentTier = extractTier(current.name);
        
        for (Block block : Vars.content.blocks()) {
            String blockBase = block.name.replaceAll("[0-9]", "");
            int blockTier = extractTier(block.name);
            
            if (blockBase.equals(baseName) && blockTier == currentTier + 1) {
                return block;
            }
        }
        return null;
    }

    int extractTier(String name) {
        // Extract number from block name (e.g., "copper-wall" = 1, "copper-wall-large" = 2)
        String numbers = name.replaceAll("[^0-9]", "");
        return numbers.isEmpty() ? 1 : Integer.parseInt(numbers);
    }

    // === POWER NETWORK ANALYSIS ===
    // Shows power grid status
    
    void addPowerNetworkInfo(Building build) {
        if (build.power != null && build.power.graph != null) {
            var graph = build.power.graph;
            float production = graph.getPowerProduced() * 60f;
            float consumption = graph.getPowerNeeded() * 60f;
            float balance = production - consumption;
            
            tooltipTable.add("[gray]─ Power Grid ─").padTop(4f).row();
            tooltipTable.add("[stat]Production: [accent]" + Strings.autoFixed(production, 1) + "/s").left().row();
            tooltipTable.add("[stat]Usage: [accent]" + Strings.autoFixed(consumption, 1) + "/s").left().row();
            
            String balanceColor = balance > 0 ? "[lime]" : "[scarlet]";
            String balanceSymbol = balance > 0 ? "+" : "";
            tooltipTable.add("[stat]Balance: " + balanceColor + balanceSymbol + Strings.autoFixed(balance, 1) + "/s").left().row();
            
            if (showWarnings && balance < 0) {
                float deficit = Math.abs(balance / production * 100f);
                tooltipTable.add("  [scarlet]⚠ Overloaded " + (int)deficit + "%").left().row();
            }
        }
    }

    // === STATIC DESCRIPTIONS ===
    // Injects info into research tree tooltips
    
    void injectStaticDescriptions() {
        for (Block block : Vars.content.blocks()) {
            if (block.description != null && !block.description.contains("§")) {
                StringBuilder extra = new StringBuilder("\n[accent]§ Stats:");
                
                if (block instanceof PowerGenerator gen) {
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
    }void addSettingsUI() {
        try {
            Vars.ui.settings.addCategory("Tooltips+", Icon.book, table -> {
                table.defaults().left().padTop(4f);
                
                // === MAIN TOGGLE ===
                table.add("[accent]═══ Main Settings ═══").center().colspan(2).padBottom(8f).row();
                
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
                
                // === DISPLAY SETTINGS ===
                table.add("[accent]═══ Display ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
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
                
                // Opacity slider
                table.add("Tooltip Opacity: ").left();
                table.slider(0, 10, 1, tooltipOpacity, v -> {
                    tooltipOpacity = (int)v;
                    saveSettings();
                }).width(200f).row();
                
                // Hover delay slider
                table.add("Hover Delay (seconds): ").left();
                table.slider(0f, 1f, 0.05f, hoverDelay, v -> {
                    hoverDelay = v;
                    saveSettings();
                }).width(200f).row();
                
                // === FEATURE TOGGLES ===
                table.add("[accent]═══ Features ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
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
                
                // === ADVANCED FEATURES ===
                table.add("[accent]═══ Advanced ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
                table.check("Power Network Analysis", showPowerDetails, v -> {
                    showPowerDetails = v;
                    saveSettings();
                }).colspan(2).left().row();
                
                table.check("Upgrade Previews", true, v -> {
                    // Future feature toggle
                    saveSettings();
                }).colspan(2).left().row();
                
                table.check("Conveyor Flow Tracking", showItemFlow, v -> {
                    showItemFlow = v;
                    saveSettings();
                }).colspan(2).left().row();
                
                // === INFO & HELP ===
                table.add("[accent]═══ Info ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
                table.add("[lightgray]Hover over buildings and units\nto see detailed tooltips").colspan(2).center().padTop(8f).row();
                
                table.add("[sky]Version 2.0 - Mobile Optimized").colspan(2).center().padTop(8f).row();
                
                // === PRESET BUTTONS ===
                table.add("[accent]═══ Quick Presets ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
                Table presetButtons = new Table();
                
                presetButtons.button("Minimal", Icon.zoom, () -> {
                    applyPreset("minimal");
                    Vars.ui.showInfo("[lime]Applied Minimal preset");
                }).size(140f, 50f).pad(4f);
                
                presetButtons.button("Balanced", Icon.settings, () -> {
                    applyPreset("balanced");
                    Vars.ui.showInfo("[lime]Applied Balanced preset");
                }).size(140f, 50f).pad(4f);
                
                presetButtons.button("Maximum", Icon.zoom, () -> {
                    applyPreset("maximum");
                    Vars.ui.showInfo("[lime]Applied Maximum preset");
                }).size(140f, 50f).pad(4f);
                
                table.add(presetButtons).colspan(2).center().padTop(8f).row();
                
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

    // === PRESET CONFIGURATIONS ===
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
                tooltipOpacity = 6;
                hoverDelay = 0.3f;
                break;
                
            case "balanced":
                enabled = true;
                compactMode = false;
                showPowerDetails = true;
                showItemFlow = true;
                showUnitAdvanced = true;
                showWarnings = true;
                showIcons = true;
                tooltipOpacity = 8;
                hoverDelay = 0.15f;
                break;
                
            case "maximum":
                enabled = true;
                compactMode = false;
                showPowerDetails = true;
                showItemFlow = true;
                showUnitAdvanced = true;
                showWarnings = true;
                showIcons = true;
                tooltipOpacity = 9;
                hoverDelay = 0.05f;
                break;
        }
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
        tooltipOpacity = 8;
        followCursor = true;
        hoverDelay = 0.15f;
        saveSettings();
    }

    // === UTILITY METHODS ===
    
    // Format large numbers cleanly
    String formatNumber(float num) {
        if (num >= 1000000) {
            return Strings.autoFixed(num / 1000000f, 1) + "M";
        } else if (num >= 1000) {
            return Strings.autoFixed(num / 1000f, 1) + "K";
        }
        return Strings.autoFixed(num, 1);
    }
    
    // Get color based on percentage
    String getPercentColor(float percent) {
        if (percent > 75f) return "[lime]";
        if (percent > 50f) return "[yellow]";
        if (percent > 25f) return "[orange]";
        return "[scarlet]";
    }
    
    // Create progress bar string
    String makeProgressBar(float current, float max, int width) {
        int filled = (int)((current / max) * width);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("]");
        return bar.toString();
    }

    // === EXPERIMENTAL FEATURES ===
    // These are bonus features you can enable later
    
    void addExperimentalFeatures(Building build) {
        // Resource efficiency meter
        if (build instanceof GenericCrafter crafter) {
            tooltipTable.add("[gray]─ Efficiency ─").padTop(4f).row();
            String bar = makeProgressBar(crafter.efficiency, 1f, 10);
            tooltipTable.add(bar + " " + (int)(crafter.efficiency * 100) + "%").left().row();
        }
        
        // Block age (time since placed)
        if (build.tile != null) {
            float age = (Time.time - build.tile.build.lastAccessed) / 60f;
            if (age > 60f) {
                tooltipTable.add("[gray]Age: " + (int)(age / 60f) + "m").left().row();
            }
        }
    }

    // Future: Blueprint cost calculator
    void showBlueprintCost(/* parameters */) {
        // This would show total resource requirements for a blueprint
        // Implementation depends on blueprint API access
    }

    // Future: Real-time alerts system
    void checkAlerts() {
        if (!showWarnings) return;
        
        // Could check for:
        // - Low power buildings
        // - Full storage blocks
        // - Damaged structures < 30% HP
        // - Units under attack
        // etc.
    }

    // === PERFORMANCE MONITORING ===
    void logPerformanceMetrics() {
        if (updateInterval.get(60)) { // Every 1 second
            int visibleTooltips = tooltipTable.visible ? 1 : 0;
            // Could log FPS impact, memory usage, etc.
            // For debugging only
        }
    }
}