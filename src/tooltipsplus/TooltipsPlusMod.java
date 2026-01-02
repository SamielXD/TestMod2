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
import mindustry.world.blocks.units.*;
import mindustry.world.meta.*;
import arc.graphics.*;
import arc.struct.*;
import mindustry.entities.*;

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
    
    boolean showHealthBars = true;
    boolean showShieldStacks = true;
    boolean showRangeIndicators = true;
    boolean showEffectRanges = true;
    boolean animateRanges = true;
    boolean showTeamRanges = true;
    boolean showUnitRanges = true;
    
    int tooltipOpacity = 8;
    boolean followCursor = true;
    boolean showIcons = true;
    int fontSize = 1;
    String colorTheme = "default";
    boolean playHoverSound = false;
    boolean highlightHovered = true;
    int maxTooltipLines = 20;
    
    float rangeOpacity = 0.25f;
    float effectRangeOpacity = 0.15f;
    float healthBarHeight = 6f;
    float shieldBarHeight = 4f;
    
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
    
    String statColor = "[stat]";
    String accentColor = "[accent]";
    String warningColor = "[scarlet]";
    String successColor = "[lime]";
    String infoColor = "[lightgray]";
    
    Seq<RangeData> rangeCache = new Seq<>();
    float rangeCacheTimer = 0f;
    float animationTimer = 0f;
    
    static final float RANGE_UPDATE = 30f;
    static final float HEALTH_BAR_OFFSET = 12f;
    static final float SHIELD_BAR_OFFSET = 18f;
    static final Color SHIELD_COLOR = Color.valueOf("84f491");
    static final Color HEALTH_COLOR = Color.valueOf("98ffa9");
    static final Color DAMAGE_COLOR = Color.valueOf("ff6b6b");
    static final Color RANGE_ATTACK = Color.valueOf("ff6b6b");
    static final Color RANGE_EFFECT = Color.valueOf("84f491");
    static final Color RANGE_REPAIR = Color.valueOf("ffd37f");
    
    static class RangeData {
        float x, y, range;
        Color color;
        boolean isPulsing;
        
        RangeData(float x, float y, float range, Color color, boolean pulse) {
            this.x = x;
            this.y = y;
            this.range = range;
            this.color = color;
            this.isPulsing = pulse;
        }
    }
    
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
        
        showHealthBars = arc.Core.settings.getBool("ttp-healthbars", true);
        showShieldStacks = arc.Core.settings.getBool("ttp-shields", true);
        showRangeIndicators = arc.Core.settings.getBool("ttp-ranges", true);
        showEffectRanges = arc.Core.settings.getBool("ttp-effects", true);
        animateRanges = arc.Core.settings.getBool("ttp-animate", true);
        showTeamRanges = arc.Core.settings.getBool("ttp-teamrange", true);
        showUnitRanges = arc.Core.settings.getBool("ttp-unitrange", true);
        
        tooltipOpacity = arc.Core.settings.getInt("ttp-opacity", 8);
        followCursor = arc.Core.settings.getBool("ttp-follow", true);
        showIcons = arc.Core.settings.getBool("ttp-icons", true);
        fontSize = arc.Core.settings.getInt("ttp-fontsize", 1);
        colorTheme = arc.Core.settings.getString("ttp-theme", "default");
        playHoverSound = arc.Core.settings.getBool("ttp-sound", false);
        highlightHovered = arc.Core.settings.getBool("ttp-highlight", true);
        maxTooltipLines = arc.Core.settings.getInt("ttp-maxlines", 20);
        rangeOpacity = arc.Core.settings.getInt("ttp-rangeopacity", 25) / 100f;
        effectRangeOpacity = arc.Core.settings.getInt("ttp-effectopacity", 15) / 100f;
        
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
        arc.Core.settings.put("ttp-healthbars", showHealthBars);
        arc.Core.settings.put("ttp-shields", showShieldStacks);
        arc.Core.settings.put("ttp-ranges", showRangeIndicators);
        arc.Core.settings.put("ttp-effects", showEffectRanges);
        arc.Core.settings.put("ttp-animate", animateRanges);
        arc.Core.settings.put("ttp-teamrange", showTeamRanges);
        arc.Core.settings.put("ttp-unitrange", showUnitRanges);
        arc.Core.settings.put("ttp-opacity", tooltipOpacity);
        arc.Core.settings.put("ttp-follow", followCursor);
        arc.Core.settings.put("ttp-icons", showIcons);
        arc.Core.settings.put("ttp-fontsize", fontSize);
        arc.Core.settings.put("ttp-theme", colorTheme);
        arc.Core.settings.put("ttp-sound", playHoverSound);
        arc.Core.settings.put("ttp-highlight", highlightHovered);
        arc.Core.settings.put("ttp-maxlines", maxTooltipLines);
        arc.Core.settings.put("ttp-rangeopacity", (int)(rangeOpacity * 100));
        arc.Core.settings.put("ttp-effectopacity", (int)(effectRangeOpacity * 100));
        arc.Core.settings.forceSave();
    }

    @Override
    public void init() {
        Log.info("TooltipsPlus v4.0 initializing...");
        if (enabled) {
            setupTooltipSystem();
            setupVisualIndicators();
            setupHotkeys();
            injectStaticDescriptions();
        }
        addSettingsUI();
        Log.info("TooltipsPlus loaded with visual indicators");
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

    String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    // Continued in Part 2...
}// Part 2/6 - Setup and Visual Systems
// Add to TooltipsPlusMod class after Part 1

void setupHotkeys() {
    Events.run(EventType.Trigger.update, () -> {
        if (arc.Core.input.keyTap(arc.input.KeyCode.t)) {
            enabled = !enabled;
            saveSettings();
            Vars.ui.showInfoToast(enabled ? "[lime]Tooltips ON" : "[scarlet]Tooltips OFF", 2f);
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
        
        if (arc.Core.input.keyTap(arc.input.KeyCode.r)) {
            showRangeIndicators = !showRangeIndicators;
            saveSettings();
        }
        
        if (arc.Core.input.keyTap(arc.input.KeyCode.h)) {
            showHealthBars = !showHealthBars;
            saveSettings();
        }
    });
}

void setupVisualIndicators() {
    Events.run(EventType.Trigger.draw, () -> {
        if (!enabled || Vars.state.isMenu()) return;
        
        animationTimer += Time.delta / 60f;
        
        if (showHealthBars) {
            drawHealthIndicators();
        }
        
        if (showRangeIndicators) {
            updateRangeCache();
            drawRangeIndicators();
        }
        
        if (showEffectRanges) {
            drawEffectRanges();
        }
    });
}

void drawHealthIndicators() {
    Groups.unit.each(unit -> {
        if (!unit.isValid() || unit.dead) return;
        
        float x = unit.x;
        float y = unit.y + unit.hitSize / 2f + HEALTH_BAR_OFFSET;
        
        drawHealthBar(x, y, unit.health, unit.maxHealth, unit.hitSize * 0.8f);
        
        if (showShieldStacks && unit.shield > 0) {
            float shieldY = y + SHIELD_BAR_OFFSET - HEALTH_BAR_OFFSET;
            drawShieldBar(x, shieldY, unit.shield, unit.maxHealth, unit.hitSize * 0.8f);
            
            if (unit.shield > unit.maxHealth) {
                int stacks = (int)(unit.shield / unit.maxHealth);
                Fonts.outline.draw("x" + stacks, x + unit.hitSize * 0.5f, shieldY + 2f, Color.white, 0.5f, false, Align.left);
            }
        }
    });
    
    Groups.build.each(build -> {
        if (!build.isValid() || build.dead) return;
        
        float x = build.x;
        float y = build.y + build.block.size * 4f + HEALTH_BAR_OFFSET;
        float width = build.block.size * 7f;
        
        drawHealthBar(x, y, build.health, build.maxHealth, width);
    });
}

void drawHealthBar(float x, float y, float health, float maxHealth, float width) {
    float percent = Math.min(health / maxHealth, 1f);
    
    Draw.color(Color.black, 0.5f);
    Fill.rect(x, y, width + 2f, healthBarHeight + 2f);
    
    Color barColor = percent > 0.6f ? HEALTH_COLOR : percent > 0.3f ? Color.yellow : DAMAGE_COLOR;
    Draw.color(barColor);
    Fill.rect(x - width / 2f + (width * percent) / 2f, y, width * percent, healthBarHeight);
    
    Draw.reset();
}

void drawShieldBar(float x, float y, float shield, float maxHealth, float width) {
    float percent = Math.min(shield / maxHealth, 1f);
    
    Draw.color(Color.black, 0.5f);
    Fill.rect(x, y, width + 2f, shieldBarHeight + 2f);
    
    Draw.color(SHIELD_COLOR, 0.8f);
    Fill.rect(x - width / 2f + (width * percent) / 2f, y, width * percent, shieldBarHeight);
    
    Draw.reset();
}

void updateRangeCache() {
    rangeCacheTimer += Time.delta / 60f;
    if (rangeCacheTimer < RANGE_UPDATE) return;
    
    rangeCacheTimer = 0f;
    rangeCache.clear();
    
    if (showUnitRanges) {
        Groups.unit.each(unit -> {
            if (!unit.isValid() || unit.dead || unit.type.weapons.size == 0) return;
            if (unit.team != Vars.player.team() && !showTeamRanges) return;
            
            float maxRange = 0f;
            for (var weapon : unit.type.weapons) {
                if (weapon.bullet != null) {
                    float weaponRange = weapon.bullet.rangeChange + weapon.bullet.speed * weapon.bullet.lifetime;
                    if (weaponRange > maxRange) {
                        maxRange = weaponRange;
                    }
                }
            }
            
            if (maxRange > 0) {
                Color color = unit.team == Vars.player.team() ? RANGE_ATTACK : Color.valueOf("ff9999");
                rangeCache.add(new RangeData(unit.x, unit.y, maxRange, color, true));
            }
        });
    }
    
    Groups.build.each(build -> {
        if (!build.isValid() || build.dead) return;
        if (build.team != Vars.player.team() && !showTeamRanges) return;
        
        if (build.block instanceof Turret) {
            Turret turret = (Turret)build.block;
            Color color = build.team == Vars.player.team() ? RANGE_ATTACK : Color.valueOf("ff9999");
            rangeCache.add(new RangeData(build.x, build.y, turret.range, color, true));
        }
    });
}

void drawRangeIndicators() {
    float pulseScale = animateRanges ? 1f + arc.math.Mathf.sin(animationTimer * 2f) * 0.05f : 1f;
    
    for (RangeData range : rangeCache) {
        float drawRange = range.isPulsing ? range.range * pulseScale : range.range;
        
        Draw.color(range.color, rangeOpacity);
        Fill.circle(range.x, range.y, drawRange);
        
        Draw.color(range.color, rangeOpacity * 2f);
        Lines.stroke(2f);
        Lines.circle(range.x, range.y, drawRange);
        
        Draw.reset();
    }
}

void drawEffectRanges() {
    Groups.build.each(build -> {
        if (!build.isValid() || build.dead) return;
        if (build.team != Vars.player.team()) return;
        
        Color effectColor = null;
        float range = 0f;
        
        if (build.block.name.contains("mend")) {
            effectColor = RANGE_REPAIR;
            range = 60f;
        } else if (build.block.name.contains("overdrive")) {
            effectColor = RANGE_EFFECT;
            range = 80f;
        } else if (build.block instanceof RepairTurret) {
            RepairTurret rt = (RepairTurret)build.block;
            effectColor = RANGE_REPAIR;
            range = rt.repairRadius;
        } else if (build.block instanceof UnitFactory) {
            effectColor = Color.cyan;
            range = 40f;
        }
        
        if (effectColor != null && range > 0) {
            Draw.color(effectColor, effectRangeOpacity);
            Fill.circle(build.x, build.y, range);
            
            Draw.color(effectColor, effectRangeOpacity * 2f);
            Lines.stroke(1.5f);
            Lines.dashCircle(build.x, build.y, range);
            
            Draw.reset();
        }
    });
}

void updateProductionTracking() {
    if (lastHoveredBuilding != null && lastHoveredBuilding.block instanceof GenericCrafter) {
        float status = lastHoveredBuilding.power != null ? lastHoveredBuilding.power.status : 0f;
        productionHistory[historyIndex] = status;
        historyIndex = (historyIndex + 1) % 60;
    }
}

// Continued in Part 3...// Part 3/6 - Tooltip System
// Add to TooltipsPlusMod class after Part 2

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
        
        if (updateInterval.get(0, 60f)) {
            updateProductionTracking();
        }
        
        Vec2 mousePos = arc.Core.input.mouseWorld();
        Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
        Building hoveredBuilding = (hoverTile != null) ? hoverTile.build : null;
        
        Unit hoveredUnit = Groups.unit.find(u -> {
            return u.within(mousePos.x, mousePos.y, u.hitSize / 2f);
        });
        
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
            Lines.rect(hoveredBuilding.x - hoveredBuilding.block.size * 4f, 
                      hoveredBuilding.y - hoveredBuilding.block.size * 4f,
                      hoveredBuilding.block.size * 8f,
                      hoveredBuilding.block.size * 8f);
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
    
    tooltipTable.add("🛡 " + statColor + "HP: " + healthColor + (int)build.health + infoColor + "/" + (int)build.maxHealth).left().row();
    
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
        addLiquidInfo(build);
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
    String healthColor = getPercentColor(healthPercent);
    
    tooltipTable.add("🛡" + statColor + "HP: " + healthColor + (int)unit.health + infoColor + "/" + (int)unit.maxHealth).left().row();
    
    if (showShieldStacks && unit.shield > 0) {
        tooltipTable.add("🛡" + statColor + "Shield: " + successColor + (int)unit.shield).left().row();
        if (unit.shield > unit.maxHealth) {
            int stacks = (int)(unit.shield / unit.maxHealth);
            tooltipTable.add("  " + infoColor + "Multi-layer (x" + stacks + ")").left().row();
        }
    }
    
    if (unit.type.armor > 0) {
        tooltipTable.add("  " + infoColor + "Armor: " + accentColor + (int)unit.type.armor).left().row();
    }
    
    if (showWarnings && healthPercent < 25f) {
        tooltipTable.add("  " + warningColor + "⚠ Critical HP!").left().row();
    }
    
    if (showUnitAdvanced) {
        String moveIcon = unit.type.flying ? "✈" : "⛏";
        tooltipTable.add(moveIcon + statColor + "Speed: " + accentColor + Strings.autoFixed(unit.type.speed * 60f, 1)).left().row();
        
        if (unit.type.mineSpeed > 0) {
            tooltipTable.add("⛏" + statColor + "Mine: " + accentColor + Strings.autoFixed(unit.type.mineSpeed, 1) + "/s").left().row();
        }
        
        if (unit.type.buildSpeed > 0) {
            tooltipTable.add("🔨" + statColor + "Build: " + accentColor + Strings.autoFixed(unit.type.buildSpeed, 1) + "/s").left().row();
        }
        
        if (unit.type.itemCapacity > 0) {
            int carrying = unit.stack != null && unit.stack.item != null ? unit.stack.amount : 0;
            String carryColor = carrying > 0 ? accentColor : infoColor;
            tooltipTable.add("📦" + statColor + "Carry: " + carryColor + carrying + infoColor + "/" + unit.type.itemCapacity).left().row();
        }
    }
    
    if (showUnitAdvanced && unit.type.weapons.size > 0) {
        float totalDPS = 0f;
        for (var weapon : unit.type.weapons) {
            if (weapon.bullet != null) {
                float dps = weapon.bullet.damage * (60f / weapon.reload);
                totalDPS += dps;
            }
        }
        
        if (totalDPS > 0) {
            tooltipTable.add("⚔" + statColor + "DPS: " + accentColor + Strings.autoFixed(totalDPS, 1)).left().row();
        }
    }
    
    positionTooltip();
    tooltipTable.pack();
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

// Continued in Part 4...// Part 4/6 - Tooltip Details
// Add to TooltipsPlusMod class after Part 3

void addPowerInfo(Building build) {
    if (build.power == null) return;
    
    tooltipTable.add(statColor + "─ Power ─").padTop(4f).row();
    
    if (build.block.consPower != null && build.block.consPower.capacity > 0) {
        float stored = build.power.status * build.block.consPower.capacity;
        float capacity = build.block.consPower.capacity;
        
        String powerBar = makeProgressBar(stored, capacity, 10);
        tooltipTable.add("⚡" + statColor + "Battery: " + accentColor + (int)stored + infoColor + "/" + (int)capacity).left().row();
        if (!compactMode) {
            tooltipTable.add("  " + powerBar).left().row();
        }
    }
    
    if (build.block instanceof PowerGenerator) {
        PowerGenerator gen = (PowerGenerator)build.block;
        float production = gen.powerProduction * 60f;
        tooltipTable.add("  " + successColor + "+ " + formatNumber(production) + "/s").left().row();
    }
    
    if (build.block.consPower != null && build.block.consPower.usage > 0) {
        float usage = build.block.consPower.usage * 60f;
        tooltipTable.add("  " + warningColor + "- " + formatNumber(usage) + "/s").left().row();
    }
}

void addItemStorageInfo(Building build) {
    if (build.items == null || build.items.total() == 0) return;
    
    tooltipTable.add(statColor + "─ Items ─").padTop(4f).row();
    
    int total = build.items.total();
    int capacity = build.block.itemCapacity;
    float fillPercent = (total / (float)capacity) * 100f;
    
    tooltipTable.add("📦 " + statColor + "Storage: " + getPercentColor(fillPercent) + total + infoColor + "/" + capacity).left().row();
    
    if (!compactMode && fillPercent > 0) {
        String storageBar = makeProgressBar(total, capacity, 10);
        tooltipTable.add("  " + storageBar).left().row();
    }
    
    if (showWarnings && fillPercent > 90f) {
        tooltipTable.add("  " + warningColor + "⚠ Nearly Full!").left().row();
    }
    
    int itemCount = 0;
    for (int i = 0; i < Vars.content.items().size && itemCount < 3; i++) {
        var item = Vars.content.item(i);
        int amount = build.items.get(item);
        if (amount > 0) {
            tooltipTable.add("  " + item.emoji() + " " + infoColor + item.localizedName + ": " + accentColor + amount).left().row();
            itemCount++;
        }
    }
}

void addLiquidInfo(Building build) {
    if (build.liquids == null || build.liquids.currentAmount() < 0.01f) return;
    
    tooltipTable.add(statColor + "─ Liquids ─").padTop(4f).row();
    
    float total = build.liquids.currentAmount();
    float capacity = build.block.liquidCapacity;
    float fillPercent = (total / capacity) * 100f;
    
    tooltipTable.add("💧" + statColor + "Tank: " + getPercentColor(fillPercent) + Strings.autoFixed(total, 1) + infoColor + "/" + Strings.autoFixed(capacity, 1)).left().row();
    
    if (!compactMode) {
        String liquidBar = makeProgressBar(total, capacity, 10);
        tooltipTable.add("  " + liquidBar).left().row();
    }
}

void addProductionInfo(Building build) {
    if (build.block instanceof GenericCrafter) {
        GenericCrafter crafter = (GenericCrafter)build.block;
        
        if (crafter.outputItems != null && crafter.outputItems.length > 0) {
            tooltipTable.add(statColor + "─ Production ─").padTop(4f).row();
            
            for (var output : crafter.outputItems) {
                float rate = (output.amount / crafter.craftTime) * 60f;
                tooltipTable.add("  → " + output.item.emoji() + " " + infoColor + Strings.autoFixed(rate, 1) + "/s").left().row();
            }
            
            float efficiency = build.efficiency;
            if (efficiency < 1f) {
                tooltipTable.add("  " + warningColor + "⚠ " + (int)(efficiency * 100f) + "% speed").left().row();
            }
        }
    }
}

void addTurretInfo(Building build) {
    if (!(build.block instanceof Turret)) return;
    
    Turret turret = (Turret)build.block;
    
    tooltipTable.add(statColor + "─ Turret ─").padTop(4f).row();
    
    tooltipTable.add("  " + infoColor + "Range: " + accentColor + (int)(turret.range / 8f) + " tiles").left().row();
    
    if (turret.reload > 0) {
        float shotsPerMin = (60f / turret.reload) * 60f;
        tooltipTable.add("  " + infoColor + "Rate: " + accentColor + Strings.autoFixed(shotsPerMin, 1) + "/min").left().row();
    }
    
    if (build instanceof Turret.TurretBuild) {
        Turret.TurretBuild tb = (Turret.TurretBuild)build;
        if (tb.hasAmmo()) {
            tooltipTable.add("  " + successColor + "✓ Ammo Ready").left().row();
        } else {
            tooltipTable.add("  " + warningColor + "✗ No Ammo").left().row();
        }
    }
}

void addDrillInfo(Building build) {
    if (!(build.block instanceof Drill)) return;
    
    Drill drill = (Drill)build.block;
    
    tooltipTable.add(statColor + "─ Drill ─").padTop(4f).row();
    
    tooltipTable.add("  " + infoColor + "Tier: " + accentColor + drill.tier).left().row();
    
    if (drill.drillTime > 0) {
        float rate = 60f / drill.drillTime;
        tooltipTable.add("  " + infoColor + "Speed: " + accentColor + Strings.autoFixed(rate, 1) + "/s").left().row();
    }
    
    Tile tile = build.tile;
    if (tile != null && tile.drop() != null) {
        tooltipTable.add("  ⛏ " + tile.drop().emoji() + " " + infoColor + tile.drop().localizedName).left().row();
    }
}

void addTeamInfo(Building build) {
    if (!Vars.state.rules.pvp) return;
    
    tooltipTable.add(statColor + "─ Team ─").padTop(4f).row();
    tooltipTable.add("  " + build.team.emoji + " " + build.team.coloredName()).left().row();
    
    if (build.team != Vars.player.team()) {
        tooltipTable.add("  " + warningColor + "🛡 Enemy Structure").left().row();
    }
}

void addConnectionInfo(Building build) {
    int connections = build.proximity.size;
    
    if (connections > 0) {
        tooltipTable.add(statColor + "─ Connections ─").padTop(4f).row();
        tooltipTable.add("  " + infoColor + "Links: " + accentColor + connections).left().row();
    }
}

void addConveyorFlowInfo(Building build) {
    if (build.block instanceof Conveyor) {
        Conveyor conveyor = (Conveyor)build.block;
        float itemsPerSec = conveyor.speed * 60f;
        tooltipTable.add("→" + statColor + "Flow: " + accentColor + Strings.autoFixed(itemsPerSec, 1) + " items/s").left().row();
    }
}

void addPowerNetworkInfo(Building build) {
    if (build.power != null && build.power.graph != null) {
        var graph = build.power.graph;
        float production = graph.getPowerProduced() * 60f;
        float consumption = graph.getPowerNeeded() * 60f;
        float balance = production - consumption;
        
        if (production > 0 || consumption > 0) {
            tooltipTable.add(infoColor + "─ Grid ─").padTop(4f).row();
            
            String balanceColor = balance > 0 ? successColor : warningColor;
            String balanceSymbol = balance > 0 ? "+" : "";
            tooltipTable.add(statColor + "Balance: " + balanceColor + balanceSymbol + Strings.autoFixed(balance, 1) + "/s").left().row();
        }
    }
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
            if (block.health > 0) {
                extra.append("\n  HP: ").append((int)block.health);
            }
            
            block.description += extra.toString();
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

// Continued in Part 5...// Part 5/6 - Settings UI (Part 1)
// Add to TooltipsPlusMod class after Part 4

void addSettingsUI() {
    try {
        Vars.ui.settings.addCategory("Tooltips+", Icon.book, t -> {
            t.defaults().left().padTop(4f);
            
            t.add("[accent]" + repeat("═", 15) + " Main " + repeat("═", 15)).center().colspan(2).padBottom(8f).row();
            
            t.check("Enable Tooltips+", enabled, v -> {
                enabled = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.add("[lightgray]Keys: T=Toggle | P=Pin | R=Range | H=Health").colspan(2).left().padTop(4f).row();
            
            t.add("[accent]" + repeat("═", 13) + " Visual UI " + repeat("═", 13)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            t.check("Health Bars", showHealthBars, v -> {
                showHealthBars = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Shield Stacks", showShieldStacks, v -> {
                showShieldStacks = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Range Indicators", showRangeIndicators, v -> {
                showRangeIndicators = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Effect Ranges", showEffectRanges, v -> {
                showEffectRanges = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Animate Ranges", animateRanges, v -> {
                animateRanges = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Show Unit Ranges", showUnitRanges, v -> {
                showUnitRanges = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Show Team Ranges", showTeamRanges, v -> {
                showTeamRanges = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.add("Range Opacity: ").left();
            t.slider(0, 100, 5, (int)(rangeOpacity * 100), v -> {
                rangeOpacity = v / 100f;
                saveSettings();
            }).width(200f).row();
            
            t.add("[lightgray](" + (int)(rangeOpacity * 100) + "%)").colspan(2).left().padTop(-4f).row();
            
            t.add("Effect Opacity: ").left();
            t.slider(0, 100, 5, (int)(effectRangeOpacity * 100), v -> {
                effectRangeOpacity = v / 100f;
                saveSettings();
            }).width(200f).row();
            
            t.add("[lightgray](" + (int)(effectRangeOpacity * 100) + "%)").colspan(2).left().padTop(-4f).row();
            
            t.add("[accent]" + repeat("═", 14) + " Display " + repeat("═", 14)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            t.check("Compact Mode", compactMode, v -> {
                compactMode = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Follow Cursor", followCursor, v -> {
                followCursor = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Show Icons", showIcons, v -> {
                showIcons = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Highlight Hovered", highlightHovered, v -> {
                highlightHovered = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Hover Sound", playHoverSound, v -> {
                playHoverSound = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.add("Color Theme: ").left();
            t.button(colorTheme, () -> {
                String[] themes = {"default", "dark", "neon", "minimal"};
                int index = 0;
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
            
            t.add("Tooltip Opacity: ").left();
            t.slider(0, 10, 1, tooltipOpacity, v -> {
                tooltipOpacity = (int)v;
                saveSettings();
            }).width(200f).row();
            
            t.add("Hover Delay: ").left();
            t.slider(0f, 1f, 0.05f, hoverDelay, v -> {
                hoverDelay = v;
                saveSettings();
            }).width(200f).row();
            
            t.add("[lightgray](" + Strings.autoFixed(hoverDelay, 2) + "s)").colspan(2).left().padTop(-4f).row();
            
            t.add("Font Size: ").left();
            t.slider(0, 2, 1, fontSize, v -> {
                fontSize = (int)v;
                saveSettings();
            }).width(200f).row();
            
            String[] fontLabels = {"Small", "Normal", "Large"};
            t.add("[lightgray]" + fontLabels[fontSize]).colspan(2).left().padTop(-4f).row();
            
            t.add("[accent]" + repeat("═", 13) + " Features " + repeat("═", 13)).center().colspan(2).padTop(12f).padBottom(8f).row();
            
            t.check("Power Details", showPowerDetails, v -> {
                showPowerDetails = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Item Flow Rates", showItemFlow, v -> {
                showItemFlow = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Advanced Unit Info", showUnitAdvanced, v -> {
                showUnitAdvanced = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Show Warnings", showWarnings, v -> {
                showWarnings = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Turret Analytics", showTurretInfo, v -> {
                showTurretInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Drill Analytics", showDrillInfo, v -> {
                showDrillInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Connection Info", showConnectionInfo, v -> {
                showConnectionInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Storage Breakdown", showStorageBreakdown, v -> {
                showStorageBreakdown = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Production History", showProductionHistory, v -> {
                showProductionHistory = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Repair Indicators", showRepairInfo, v -> {
                showRepairInfo = v;
                saveSettings();
            }).colspan(2).left().row();
            
            t.check("Team Stats (PvP)", showTeamStats, v -> {
                showTeamStats = v;
                saveSettings();
            }).colspan(2).left().row();
            
            addPresetsAndInfo(t);
        });
    } catch (Throwable ex) {
        Log.err("TooltipsPlus: Failed to add settings UI", ex);
    }
}

// Continued in Part 6...// Part 6/6 - Settings UI (Part 2) & Presets
// Add to TooltipsPlusMod class after Part 5

void addPresetsAndInfo(Table t) {
    t.add("[accent]" + repeat("═", 12) + " Presets " + repeat("═", 12)).center().colspan(2).padTop(12f).padBottom(8f).row();
    
    Table presetRow1 = new Table();
    presetRow1.button("Minimal", Icon.zoom, () -> {
        applyPreset("minimal");
        Vars.ui.showInfo("[lime]Applied Minimal preset");
    }).size(140f, 50f).pad(4f);
    
    presetRow1.button("Balanced", Icon.settings, () -> {
        applyPreset("balanced");
        Vars.ui.showInfo("[lime]Applied Balanced preset");
    }).size(140f, 50f).pad(4f);
    
    t.add(presetRow1).colspan(2).center().padTop(8f).row();
    
    Table presetRow2 = new Table();
    presetRow2.button("Maximum", Icon.zoom, () -> {
        applyPreset("maximum");
        Vars.ui.showInfo("[lime]Applied Maximum preset");
    }).size(140f, 50f).pad(4f);
    
    presetRow2.button("Combat", Icon.units, () -> {
        applyPreset("combat");
        Vars.ui.showInfo("[lime]Applied Combat preset");
    }).size(140f, 50f).pad(4f);
    
    t.add(presetRow2).colspan(2).center().padTop(4f).row();
    
    t.add("[accent]" + repeat("═", 15) + " Info " + repeat("═", 15)).center().colspan(2).padTop(12f).padBottom(8f).row();
    
    t.add("[lightgray]Health bars, shields, and range\nindicators shown in real-time").colspan(2).center().padTop(8f).row();
    
    t.add("[sky]Version 4.0 - Visual Indicators").colspan(2).center().padTop(8f).row();
    
    t.button("Reset to Defaults", Icon.refresh, () -> {
        resetToDefaults();
        Vars.ui.showInfo("[lime]Reset to defaults");
    }).size(200f, 50f).colspan(2).center().padTop(16f);
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
            showHealthBars = true;
            showShieldStacks = false;
            showRangeIndicators = false;
            showEffectRanges = false;
            animateRanges = false;
            showUnitRanges = false;
            showTeamRanges = false;
            tooltipOpacity = 6;
            hoverDelay = 0.3f;
            colorTheme = "minimal";
            fontSize = 0;
            rangeOpacity = 0.15f;
            effectRangeOpacity = 0.1f;
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
            showHealthBars = true;
            showShieldStacks = true;
            showRangeIndicators = true;
            showEffectRanges = true;
            animateRanges = true;
            showUnitRanges = true;
            showTeamRanges = false;
            tooltipOpacity = 8;
            hoverDelay = 0.15f;
            colorTheme = "default";
            fontSize = 1;
            rangeOpacity = 0.25f;
            effectRangeOpacity = 0.15f;
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
            showHealthBars = true;
            showShieldStacks = true;
            showRangeIndicators = true;
            showEffectRanges = true;
            animateRanges = true;
            showUnitRanges = true;
            showTeamRanges = true;
            tooltipOpacity = 9;
            hoverDelay = 0.05f;
            colorTheme = "default";
            fontSize = 1;
            highlightHovered = true;
            rangeOpacity = 0.35f;
            effectRangeOpacity = 0.25f;
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
            showHealthBars = true;
            showShieldStacks = true;
            showRangeIndicators = true;
            showEffectRanges = false;
            animateRanges = true;
            showUnitRanges = true;
            showTeamRanges = true;
            tooltipOpacity = 7;
            hoverDelay = 0.1f;
            colorTheme = "neon";
            fontSize = 1;
            rangeOpacity = 0.3f;
            effectRangeOpacity = 0.2f;
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
    showHealthBars = true;
    showShieldStacks = true;
    showRangeIndicators = true;
    showEffectRanges = true;
    animateRanges = true;
    showUnitRanges = true;
    showTeamRanges = false;
    tooltipOpacity = 8;
    followCursor = true;
    hoverDelay = 0.15f;
    fontSize = 1;
    colorTheme = "default";
    playHoverSound = false;
    highlightHovered = true;
    maxTooltipLines = 20;
    rangeOpacity = 0.25f;
    effectRangeOpacity = 0.15f;
    applyColorTheme();
    saveSettings();
}

// End of TooltipsPlusMod class
}