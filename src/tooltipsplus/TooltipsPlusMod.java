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
import mindustry.world.meta.*;

public class TooltipsPlusMod extends Mod {

    // Settings
    boolean enabled = true;
    boolean showTemperature = true;
    boolean showItemFlags = true;
    boolean debug = false;

    Table tooltipTable;
    
    public TooltipsPlusMod() {
        // load settings
        enabled = arc.Core.settings.getBool("tooltipsplus-enabled", true);
        showTemperature = arc.Core.settings.getBool("tooltipsplus-temp", true);
        showItemFlags = arc.Core.settings.getBool("tooltipsplus-itemflags", true);
        debug = arc.Core.settings.getBool("tooltipsplus-debug", false);
    }

    @Override
    public void init() {
        Log.info("TooltipsPlus initializing...");
        if (enabled) {
            injectTooltips();
            setupHoverTooltips();
        }
        addSettingsUI();
        Log.info("TooltipsPlus loaded successfully");
    }

    void saveSettings() {
        arc.Core.settings.put("tooltipsplus-enabled", enabled);
        arc.Core.settings.put("tooltipsplus-temp", showTemperature);
        arc.Core.settings.put("tooltipsplus-itemflags", showItemFlags);
        arc.Core.settings.put("tooltipsplus-debug", debug);
        arc.Core.settings.forceSave();
    }

    void setupHoverTooltips() {
        // Create persistent tooltip table
        tooltipTable = new Table(Styles.black6);
        tooltipTable.margin(4f);
        tooltipTable.visible = false;
        Vars.ui.hudGroup.addChild(tooltipTable);
        
        // Hook into the render loop to show tooltips on hover
        Events.run(EventType.Trigger.draw, () -> {
            if (!enabled || Vars.state.isMenu()) {
                tooltipTable.visible = false;
                return;
            }
            
            Vec2 mousePos = arc.Core.input.mouseWorld();
            
            // Check for building under cursor
            Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
            if (hoverTile != null && hoverTile.build != null) {
                showBuildingTooltip(hoverTile.build);
                return;
            }
            
            // Check for unit under cursor
            Unit hoverUnit = Groups.unit.find(u -> {
                return u.within(mousePos.x, mousePos.y, u.hitSize);
            });
            
            if (hoverUnit != null) {
                showUnitTooltip(hoverUnit);
                return;
            }
            
            // Hide tooltip if nothing is hovered
            tooltipTable.visible = false;
        });
    }

    void showBuildingTooltip(Building build) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        tooltipTable.add("[accent]" + build.block.localizedName).row();
        tooltipTable.add("[lightgray]─────────────").row();
        
        // Power info
        if (build.block instanceof PowerGenerator gen) {
            tooltipTable.add("[stat]Power: [accent]" + (int)(gen.powerProduction * 60f) + "/s").left().row();
        }
        
        // Power consumption (check if block consumes power)
        if (build.block.consumesPower) {
            tooltipTable.add("[stat]Uses Power").left().row();
        }
        
        // Item capacity
        if (build.block.hasItems && build.block.itemCapacity > 0) {
            tooltipTable.add("[stat]Item Cap: [accent]" + build.block.itemCapacity).left().row();
            // Show current items
            if (build.items != null && build.items.total() > 0) {
                tooltipTable.add("[stat]Current: [accent]" + build.items.total()).left().row();
            }
        }
        
        // Liquid capacity
        if (build.block.hasLiquids && build.block.liquidCapacity > 0) {
            tooltipTable.add("[stat]Liquid Cap: [accent]" + Strings.autoFixed(build.block.liquidCapacity, 1)).left().row();
        }
        
        // Health
        tooltipTable.add("[stat]Health: [accent]" + (int)build.health + "/" + (int)build.maxHealth).left().row();
        
        // Position tooltip near cursor
        Vec2 screenPos = arc.Core.input.mouse();
        tooltipTable.setPosition(screenPos.x + 20f, screenPos.y + 20f);
        tooltipTable.pack();
        tooltipTable.act(arc.Core.graphics.getDeltaTime());
    }

    void showUnitTooltip(Unit unit) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        tooltipTable.add("[accent]" + unit.type.localizedName).row();
        tooltipTable.add("[lightgray]─────────────").row();
        
        tooltipTable.add("[stat]Health: [accent]" + (int)unit.health + "/" + (int)unit.maxHealth).left().row();
        tooltipTable.add("[stat]Armor: [accent]" + (int)unit.type.armor).left().row();
        tooltipTable.add("[stat]Speed: [accent]" + Strings.autoFixed(unit.type.speed, 1)).left().row();
        
        if (unit.type.flying) {
            tooltipTable.add("[stat]Flying: [accent]Yes").left().row();
        }
        
        if (unit.type.mineSpeed > 0) {
            tooltipTable.add("[stat]Mine Speed: [accent]" + Strings.autoFixed(unit.type.mineSpeed, 1)).left().row();
        }
        
        if (unit.type.buildSpeed > 0) {
            tooltipTable.add("[stat]Build Speed: [accent]" + Strings.autoFixed(unit.type.buildSpeed, 1)).left().row();
        }
        
        // Position tooltip near cursor
        Vec2 screenPos = arc.Core.input.mouse();
        tooltipTable.setPosition(screenPos.x + 20f, screenPos.y + 20f);
        tooltipTable.pack();
        tooltipTable.act(arc.Core.graphics.getDeltaTime());
    }

    void injectTooltips() {
        // Keep the research tree descriptions too
        for (Block block : Vars.content.blocks()) {
            if (block.description != null && !block.description.contains("Tooltips+ Info:")) {
                block.description += "\n[accent]Tooltips+ Info:";
                if (block instanceof PowerGenerator gen) {
                    block.description += "\nPower: " + (int)(gen.powerProduction * 60f) + "/s";
                }
                if (block.hasItems && block.itemCapacity > 0) {
                    block.description += "\nItem Cap: " + block.itemCapacity;
                }
                if (block.hasLiquids && block.liquidCapacity > 0) {
                    block.description += "\nLiquid Cap: " + Strings.autoFixed(block.liquidCapacity, 1);
                }
            }
        }

        for (UnitType unit : Vars.content.units()) {
            if (unit.description != null && !unit.description.contains("Tooltips+ Info:")) {
                unit.description += "\n[accent]Tooltips+ Info:";
                unit.description += "\nHealth: " + (int)unit.health;
                unit.description += "\nArmor: " + (int)unit.armor;
                unit.description += "\nSpeed: " + Strings.autoFixed(unit.speed, 1);
                if (unit.flying) unit.description += "\nFlying: Yes";
                if (unit.mineSpeed > 0) unit.description += "\nMine Speed: " + Strings.autoFixed(unit.mineSpeed, 1);
                if (unit.buildSpeed > 0) unit.description += "\nBuild Speed: " + Strings.autoFixed(unit.buildSpeed, 1);
            }
        }
    }

    void addSettingsUI() {
        try {
            Vars.ui.settings.addCategory("Tooltips+", Icon.book, table -> {
                table.checkPref("tooltipsplus-enabled", enabled, v -> {
                    enabled = v;
                    saveSettings();
                    Vars.ui.showInfo("[lime]" + (v ? "Enabled" : "Disabled") + " Tooltips+");
                });
                table.row();

                table.checkPref("tooltipsplus-temp", showTemperature, v -> {
                    showTemperature = v;
                    saveSettings();
                });
                table.row();

                table.checkPref("tooltipsplus-itemflags", showItemFlags, v -> {
                    showItemFlags = v;
                    saveSettings();
                });
                table.row();

                table.add("[lightgray]Hover over buildings/units to see tooltips").padTop(8f).left();
            });
        } catch (Throwable ex) {
            Log.err("TooltipsPlus: failed to add settings UI", ex);
        }
    }
}