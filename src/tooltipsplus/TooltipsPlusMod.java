package tooltipsplus;

import arc.Events;
import arc.graphics.g2d.*;
import arc.math.geom.*;
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
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.storage.*;

public class TooltipsPlusMod extends Mod {

    boolean enabled = true;
    boolean showBottlenecks = true;
    boolean showPredictions = true;
    boolean showTargeting = true;
    boolean learnerMode = false;
    boolean compactMode = false;
    
    int tooltipOpacity = 9;
    float hoverDelay = 0.08f;
    
    Table tooltipTable;
    Building lastHoveredBuilding;
    Unit lastHoveredUnit;
    float hoverTimer = 0f;
    
    float[] damageDealt = new float[100];
    float[] healingDone = new float[100];
    
    public TooltipsPlusMod() {
        loadSettings();
    }

    void loadSettings() {
        enabled = arc.Core.settings.getBool("ttp-enabled", true);
        showBottlenecks = arc.Core.settings.getBool("ttp-bottleneck", true);
        showPredictions = arc.Core.settings.getBool("ttp-predict", true);
        showTargeting = arc.Core.settings.getBool("ttp-target", true);
        learnerMode = arc.Core.settings.getBool("ttp-learner", false);
        compactMode = arc.Core.settings.getBool("ttp-compact", false);
        tooltipOpacity = arc.Core.settings.getInt("ttp-opacity", 9);
        hoverDelay = arc.Core.settings.getFloat("ttp-delay", 0.08f);
    }

    void saveSettings() {
        arc.Core.settings.put("ttp-enabled", enabled);
        arc.Core.settings.put("ttp-bottleneck", showBottlenecks);
        arc.Core.settings.put("ttp-predict", showPredictions);
        arc.Core.settings.put("ttp-target", showTargeting);
        arc.Core.settings.put("ttp-learner", learnerMode);
        arc.Core.settings.put("ttp-compact", compactMode);
        arc.Core.settings.put("ttp-opacity", tooltipOpacity);
        arc.Core.settings.put("ttp-delay", hoverDelay);
        arc.Core.settings.forceSave();
    }

    @Override
    public void init() {
        Log.info("TooltipsPlus v4.0 - Problem-Solving Tooltips");
        if (enabled) {
            setupTooltipSystem();
            setupHotkeys();
            suppressVanillaTooltips();
            trackCombatStats();
        }
        addSettingsUI();
        Log.info("TooltipsPlus v4.0 loaded - Vanilla tooltips soft-replaced");
    }

    void setupHotkeys() {
        Events.run(EventType.Trigger.update, () -> {
            if (arc.Core.input.keyTap(arc.input.KeyCode.t)) {
                enabled = !enabled;
                saveSettings();
                Vars.ui.showInfo(enabled ? "[lime]Tooltips ON" : "[scarlet]Tooltips OFF");
            }
        });
    }

    void suppressVanillaTooltips() {
        for (Block block : Vars.content.blocks()) {
            if (block.description != null && block.description.length() > 10) {
                block.description = "";
            }
            block.details = "";
        }
        
        for (UnitType unit : Vars.content.units()) {
            if (unit.description != null && unit.description.length() > 10) {
                unit.description = "";
            }
            unit.details = "";
        }
        
        for (Item item : Vars.content.items()) {
            if (item.description != null && item.description.length() > 10) {
                item.description = "";
            }
            item.details = "";
        }
        
        for (Liquid liquid : Vars.content.liquids()) {
            if (liquid.description != null && liquid.description.length() > 10) {
                liquid.description = "";
            }
            liquid.details = "";
        }
    }

    void trackCombatStats() {
        Events.on(EventType.UnitDamageEvent.class, event -> {
            if (event.unit != null) {
                int unitId = event.unit.id % 100;
                damageDealt[unitId] += event.bullet != null ? event.bullet.damage : 0f;
            }
        });
    }void setupTooltipSystem() {
        tooltipTable = new Table(Styles.black);
        tooltipTable.background(Tex.buttonEdge3);
        tooltipTable.margin(8f);
        tooltipTable.visible = false;
        Vars.ui.hudGroup.addChild(tooltipTable);
        
        tooltipTable.update(() -> {
            if (!enabled) {
                tooltipTable.visible = false;
                return;
            }
        });
        
        Events.run(EventType.Trigger.draw, () -> {
            if (!enabled || Vars.state.isMenu()) {
                tooltipTable.visible = false;
                hoverTimer = 0f;
                return;
            }
            
            Vec2 mousePos = arc.Core.input.mouseWorld();
            Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
            Building hoveredBuilding = (hoverTile != null) ? hoverTile.build : null;
            
            Unit hoveredUnit = Groups.unit.find(u -> u.within(mousePos.x, mousePos.y, u.hitSize / 2f));
            
            if (hoveredBuilding != lastHoveredBuilding || hoveredUnit != lastHoveredUnit) {
                hoverTimer = 0f;
                lastHoveredBuilding = hoveredBuilding;
                lastHoveredUnit = hoveredUnit;
            }
            
            if (hoveredBuilding != null || hoveredUnit != null) {
                hoverTimer += Time.delta / 60f;
            }
            
            if (hoverTimer >= hoverDelay) {
                if (hoveredBuilding != null) {
                    showSmartTooltip(hoveredBuilding);
                    drawVisualIndicators(hoveredBuilding);
                    return;
                } else if (hoveredUnit != null) {
                    showUnitTooltip(hoveredUnit);
                    return;
                }
            }
            
            tooltipTable.visible = false;
        });
    }

    void showSmartTooltip(Building build) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        tooltipTable.add("[accent]" + build.block.localizedName).style(Styles.outlineLabel).row();
        
        if (learnerMode) {
            String explanation = getBlockExplanation(build);
            if (explanation != null) {
                tooltipTable.add("[lightgray]" + explanation).left().width(300f).wrap().row();
                tooltipTable.row();
            }
        }
        
        String status = analyzeStatus(build);
        if (status != null) {
            tooltipTable.add(status).left().row();
        }
        
        if (showBottlenecks) {
            String bottleneck = detectBottleneck(build);
            if (bottleneck != null) {
                tooltipTable.add("[scarlet]⚠ " + bottleneck).left().row();
            }
        }
        
        if (showPredictions) {
            String prediction = makePrediction(build);
            if (prediction != null) {
                tooltipTable.add("[sky]⏱ " + prediction).left().row();
            }
        }
        
        addContextInfo(build);
        addTeamInfo(build);
        
        positionTooltip();
        tooltipTable.pack();
    }

    String getBlockExplanation(Building build) {
        if (build.block instanceof GenericCrafter) {
            return "Converts input materials into output products using power";
        } else if (build.block instanceof Drill) {
            return "Extracts resources from the ground automatically";
        } else if (build.block instanceof Turret) {
            return "Defensive structure that attacks enemies in range";
        } else if (build.block instanceof PowerGenerator) {
            return "Generates power for your base";
        } else if (build.block instanceof CoreBlock) {
            return "Main base - stores all resources and spawns units";
        } else if (build.block instanceof Conveyor) {
            return "Transports items between buildings";
        }
        return null;
    }

    String analyzeStatus(Building build) {
        float efficiency = build.efficiency;
        float health = build.health / build.maxHealth;
        
        if (health < 0.25f) {
            return "[scarlet]🔥 CRITICAL - Repair immediately!";
        } else if (health < 0.5f) {
            return "[orange]⚠ Damaged - Needs repair";
        }
        
        if (efficiency >= 1f) {
            return "[lime]✓ Operating optimally";
        } else if (efficiency > 0.7f) {
            return "[yellow]⚙ Working (" + (int)(efficiency * 100) + "%)";
        } else if (efficiency > 0.3f) {
            return "[orange]⚠ Inefficient (" + (int)(efficiency * 100) + "%)";
        } else if (efficiency > 0f) {
            return "[scarlet]⚠ Barely working (" + (int)(efficiency * 100) + "%)";
        } else {
            return "[scarlet]✗ IDLE - Not working";
        }
    }

    String detectBottleneck(Building build) {
        if (build.power != null && build.power.status < 0.1f && build.block.consPower != null && build.block.consPower.usage > 0) {
            return "No power - Connect to generator";
        }
        
        if (build.items != null && build.block instanceof GenericCrafter) {
            GenericCrafter crafter = (GenericCrafter)build.block;
            if (crafter.hasItems && build.items.total() == 0) {
                return "No input items - Check supply chain";
            }
            if (build.items.total() >= build.block.itemCapacity * 0.95f) {
                return "Output blocked - Items can't leave";
            }
        }
        
        if (build.liquids != null && build.block instanceof GenericCrafter) {
            GenericCrafter crafter = (GenericCrafter)build.block;
            if (crafter.hasLiquids && build.liquids.currentAmount() < 0.1f) {
                return "No liquid input - Check pipes";
            }
        }
        
        if (build.block instanceof Turret && build instanceof Turret.TurretBuild) {
            Turret.TurretBuild tb = (Turret.TurretBuild)build;
            if (!tb.hasAmmo()) {
                return "No ammo - Supply required";
            }
            
            boolean hasTarget = false;
            for (Unit u : Groups.unit) {
                if (u.team != build.team && u.within(build, ((Turret)build.block).range)) {
                    hasTarget = true;
                    break;
                }
            }
            if (!hasTarget) {
                return "No enemies in range";
            }
        }
        
        return null;
    }

    String makePrediction(Building build) {
        if (build.items != null && build.items.total() > 0) {
            if (build.block instanceof GenericCrafter) {
                GenericCrafter crafter = (GenericCrafter)build.block;
                if (crafter.outputItems != null && crafter.outputItems.length > 0) {
                    float rate = (crafter.outputItems[0].amount / crafter.craftTime) * 60f * build.efficiency;
                    if (rate > 0.01f) {
                        int remaining = build.block.itemCapacity - build.items.total();
                        float timeToFull = remaining / rate;
                        if (timeToFull < 60f && timeToFull > 0f) {
                            return "Storage full in " + (int)timeToFull + "s";
                        }
                    }
                }
            }
        }
        
        if (build.health < build.maxHealth) {
            float damage = build.maxHealth - build.health;
            if (damage > build.maxHealth * 0.3f) {
                return "Repair priority: HIGH";
            }
        }
        
        return null;
    }void addContextInfo(Building build) {
        if (build.block instanceof Turret) {
            Turret turret = (Turret)build.block;
            tooltipTable.add("[lightgray]Range: [accent]" + (int)(turret.range / 8f) + " tiles").left().row();
            
            if (turret.reload > 0) {
                float shotsPerMin = (60f / turret.reload) * 60f;
                tooltipTable.add("[lightgray]Fire rate: [accent]" + Strings.autoFixed(shotsPerMin, 1) + "/min").left().row();
            }
        }
        
        if (build.block instanceof Drill) {
            Drill drill = (Drill)build.block;
            Tile tile = build.tile;
            if (tile != null && tile.drop() != null) {
                tooltipTable.add("[lightgray]Mining: " + tile.drop().emoji() + " [accent]" + tile.drop().localizedName).left().row();
                
                if (drill.drillTime > 0 && build.efficiency > 0) {
                    float rate = (60f / drill.drillTime) * build.efficiency;
                    tooltipTable.add("[lightgray]Output: [accent]" + Strings.autoFixed(rate, 1) + "/s").left().row();
                }
            }
        }
        
        if (build.block instanceof CoreBlock && build.items != null) {
            tooltipTable.add("[accent]═ Core Storage ═").center().row();
            int count = 0;
            for (int i = 0; i < Vars.content.items().size && count < 12; i++) {
                var item = Vars.content.item(i);
                int amount = build.items.get(item);
                if (amount > 0) {
                    tooltipTable.add(item.emoji() + " [lightgray]" + amount).left().row();
                    count++;
                }
            }
        }
        
        if (build.block instanceof PowerGenerator) {
            PowerGenerator gen = (PowerGenerator)build.block;
            float production = gen.powerProduction * 60f * build.efficiency;
            tooltipTable.add("[lightgray]Power: [lime]+" + Strings.autoFixed(production, 1) + "/s").left().row();
        }
    }

    void addTeamInfo(Building build) {
        if (build.team != Vars.player.team()) {
            tooltipTable.add("[scarlet]⚠ ENEMY - " + build.team.emoji + " " + build.team.name).left().row();
        } else if (Vars.state.rules.pvp) {
            tooltipTable.add("[lime]Allied - " + build.team.emoji + " " + build.team.name).left().row();
        }
    }

    void drawVisualIndicators(Building build) {
        float health = build.health / build.maxHealth;
        float efficiency = build.efficiency;
        
        arc.graphics.Color borderColor = null;
        if (health < 0.3f) {
            borderColor = arc.graphics.Color.red;
        } else if (efficiency < 0.5f) {
            borderColor = arc.graphics.Color.orange;
        } else if (efficiency >= 1f) {
            borderColor = arc.graphics.Color.lime;
        }
        
        if (borderColor != null) {
            Lines.stroke(3f);
            Draw.color(borderColor, 0.7f);
            Lines.rect(build.x - build.block.size * 4f, 
                      build.y - build.block.size * 4f,
                      build.block.size * 8f,
                      build.block.size * 8f);
            Draw.reset();
        }
        
        if (showTargeting && build.block instanceof Turret) {
            Turret turret = (Turret)build.block;
            
            Lines.stroke(2f);
            Draw.color(arc.graphics.Color.cyan, 0.3f);
            Lines.circle(build.x, build.y, turret.range);
            
            if (build instanceof Turret.TurretBuild) {
                Turret.TurretBuild tb = (Turret.TurretBuild)build;
                float rotation = tb.rotation;
                float range = turret.range * 0.8f;
                
                float targetX = build.x + arc.math.Angles.trnsx(rotation, range);
                float targetY = build.y + arc.math.Angles.trnsy(rotation, range);
                
                Lines.stroke(2f);
                Draw.color(arc.graphics.Color.red, 0.6f);
                Lines.line(build.x, build.y, targetX, targetY);
                
                Fill.circle(targetX, targetY, 4f);
            }
            
            Draw.reset();
        }
    }

    void showUnitTooltip(Unit unit) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        tooltipTable.add("[accent]" + unit.type.localizedName).style(Styles.outlineLabel).row();
        
        float healthPercent = (unit.health / unit.maxHealth) * 100f;
        String healthColor = healthPercent > 75f ? "[lime]" : healthPercent > 40f ? "[yellow]" : "[scarlet]";
        
        tooltipTable.add(healthColor + "HP: " + (int)unit.health + "[lightgray]/" + (int)unit.maxHealth + " (" + (int)healthPercent + "%)").left().row();
        
        if (unit.type.armor > 0) {
            tooltipTable.add("[lightgray]Armor: [accent]" + (int)unit.type.armor).left().row();
        }
        
        if (learnerMode) {
            String role = getUnitRole(unit);
            if (role != null) {
                tooltipTable.add("[lightgray]" + role).left().width(250f).wrap().row();
            }
        }
        
        String activity = getUnitActivity(unit);
        if (activity != null) {
            tooltipTable.add(activity).left().row();
        }
        
        if (unit.stack != null && unit.stack.item != null && unit.stack.amount > 0) {
            tooltipTable.add("[lightgray]Carrying: " + unit.stack.item.emoji() + " [accent]" + unit.stack.amount + "[lightgray]/" + unit.type.itemCapacity).left().row();
        }
        
        if (unit.type.weapons.size > 0) {
            addWeaponStats(unit);
        }
        
        addUnitDamageStats(unit);
        
        if (unit.team != Vars.player.team()) {
            tooltipTable.add("[scarlet]⚠ ENEMY - " + unit.team.emoji + " " + unit.team.name).left().row();
        } else if (unit.isPlayer()) {
            tooltipTable.add("[royal]👤 Player Controlled").left().row();
        } else {
            tooltipTable.add("[lime]Allied - " + unit.team.emoji + " " + unit.team.name).left().row();
        }
        
        positionTooltip();
        tooltipTable.pack();
    }

    String getUnitRole(Unit unit) {
        if (unit.type.flying) {
            if (unit.type.weapons.size > 0) {
                return "Flying combat unit - Fast but fragile";
            }
            return "Flying support unit - Transport and building";
        } else {
            if (unit.type.mineSpeed > 0) {
                return "Ground unit - Mining and construction";
            } else if (unit.type.weapons.size > 0) {
                return "Ground combat unit - Tank role";
            }
        }
        return null;
    }

    String getUnitActivity(Unit unit) {
        if (unit.mineTile != null) {
            return "[lime]⛏ Mining resources";
        } else if (unit.activelyBuilding()) {
            return "[lime]🔨 Constructing";
        } else if (unit.isShooting) {
            return "[scarlet]⚔ In combat";
        } else if (unit.moving()) {
            return "[sky]→ Moving";
        } else {
            return "[lightgray]○ Idle";
        }
    }

    void addWeaponStats(Unit unit) {
        float totalDPS = 0f;
        for (var weapon : unit.type.weapons) {
            if (weapon.bullet != null && weapon.reload > 0) {
                float dps = weapon.bullet.damage * (60f / weapon.reload);
                totalDPS += dps;
            }
        }
        
        if (totalDPS > 0) {
            tooltipTable.add("[lightgray]DPS: [scarlet]" + Strings.autoFixed(totalDPS, 1)).left().row();
            
            if (unit.type.weapons.size > 1) {
                tooltipTable.add("[lightgray]Weapons: [accent]" + unit.type.weapons.size).left().row();
            }
        }
    }

    void addUnitDamageStats(Unit unit) {
        int unitId = unit.id % 100;
        
        if (damageDealt[unitId] > 0) {
            tooltipTable.add("[scarlet]⚔ Damage dealt: " + (int)damageDealt[unitId]).left().row();
        }
        
        if (healingDone[unitId] > 0) {
            tooltipTable.add("[lime]❤ Healing done: " + (int)healingDone[unitId]).left().row();
        }
    }

    void positionTooltip() {
        Vec2 screenPos = arc.Core.input.mouse();
        float x = screenPos.x + 15f;
        float y = screenPos.y + 15f;
        
        if (x + tooltipTable.getWidth() > arc.Core.graphics.getWidth()) {
            x = screenPos.x - tooltipTable.getWidth() - 10f;
        }
        if (y + tooltipTable.getHeight() > arc.Core.graphics.getHeight()) {
            y = screenPos.y - tooltipTable.getHeight() - 10f;
        }
        
        tooltipTable.setPosition(x, y);
        tooltipTable.color.a = tooltipOpacity / 10f;
    }void addSettingsUI() {
        try {
            Vars.ui.settings.addCategory("Tooltips+", Icon.book, table -> {
                table.defaults().left().padTop(4f);
                
                table.add("[accent]═══ TooltipsPlus v4.0 ═══").center().colspan(2).padBottom(8f).row();
                table.add("[lightgray]Problem-solving tooltips that help you understand issues").center().colspan(2).padBottom(12f).row();
                
                table.check("Enable Tooltips+", enabled, v -> {
                    enabled = v;
                    saveSettings();
                    if (v) {
                        setupTooltipSystem();
                        suppressVanillaTooltips();
                        trackCombatStats();
                    }
                }).colspan(2).left().row();
                
                table.add("[accent]═══ Core Features ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
                table.check("Bottleneck Detection", showBottlenecks, v -> {
                    showBottlenecks = v;
                    saveSettings();
                }).colspan(2).left().row();
                table.add("[darkgray]Shows why buildings aren't working").colspan(2).left().padTop(-4f).row();
                
                table.check("Time Predictions", showPredictions, v -> {
                    showPredictions = v;
                    saveSettings();
                }).colspan(2).left().padTop(8f).row();
                table.add("[darkgray]Predicts when storage fills/empties").colspan(2).left().padTop(-4f).row();
                
                table.check("Turret Targeting Lines", showTargeting, v -> {
                    showTargeting = v;
                    saveSettings();
                }).colspan(2).left().padTop(8f).row();
                table.add("[darkgray]Shows where turrets are aiming").colspan(2).left().padTop(-4f).row();
                
                table.check("Learner Mode", learnerMode, v -> {
                    learnerMode = v;
                    saveSettings();
                }).colspan(2).left().padTop(8f).row();
                table.add("[darkgray]Explains what blocks do (for new players)").colspan(2).left().padTop(-4f).row();
                
                table.check("Compact Mode", compactMode, v -> {
                    compactMode = v;
                    saveSettings();
                }).colspan(2).left().padTop(8f).row();
                
                table.add("[accent]═══ Advanced ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                
                table.add("Tooltip Opacity: ").left();
                table.slider(5, 10, 1, tooltipOpacity, v -> {
                    tooltipOpacity = (int)v;
                    saveSettings();
                }).width(200f).row();
                
                table.add("Hover Delay: ").left();
                table.slider(0f, 0.5f, 0.05f, hoverDelay, v -> {
                    hoverDelay = v;
                    saveSettings();
                }).width(200f).row();
                table.add("[darkgray]Current: " + Strings.autoFixed(hoverDelay, 2) + "s").colspan(2).left().padTop(-4f).row();
                
                table.add("[accent]═══ Info ═══").center().colspan(2).padTop(12f).padBottom(8f).row();
                table.add("[lightgray]Press T to toggle tooltips").colspan(2).center().row();
                table.add("[sky]Features: Damage tracking, predictions, smart analysis").colspan(2).center().padTop(4f).row();
                table.add("[lime]Vanilla tooltips replaced automatically").colspan(2).center().padTop(4f).row();
                
                table.button("Reset Settings", Icon.refresh, () -> {
                    enabled = true;
                    showBottlenecks = true;
                    showPredictions = true;
                    showTargeting = true;
                    learnerMode = false;
                    compactMode = false;
                    tooltipOpacity = 9;
                    hoverDelay = 0.08f;
                    saveSettings();
                    Vars.ui.showInfo("[lime]Settings reset");
                }).size(200f, 50f).colspan(2).center().padTop(16f);
                
                table.button("Clear Combat Stats", Icon.trash, () -> {
                    for (int i = 0; i < damageDealt.length; i++) {
                        damageDealt[i] = 0;
                        healingDone[i] = 0;
                    }
                    Vars.ui.showInfo("[lime]Combat stats cleared");
                }).size(200f, 50f).colspan(2).center().padTop(8f);
            });
        } catch (Throwable ex) {
            Log.err("TooltipsPlus: Failed to add settings UI", ex);
        }
    }
}