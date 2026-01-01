package tooltipsplus;

import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.*;
import mindustry.*;
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
        // Hook into the UI update loop to show tooltips on hover
        Vars.ui.hudGroup.update(() -> {
            if (!enabled) return;
            
            // Get the tile/unit under cursor
            Tile hoverTile = Vars.world.tileWorld(arc.Core.input.mouseWorld().x, arc.Core.input.mouseWorld().y);
            
            // Show tooltip for buildings
            if (hoverTile != null && hoverTile.build != null) {
                Building build = hoverTile.build;
                showBuildingTooltip(build);
            }
            
            // Show tooltip for units
            Unit hoverUnit = Units.closestOverlap(Vars.player.team(), arc.Core.input.mouseWorld().x, arc.Core.input.mouseWorld().y, 8f, u -> true);
            if (hoverUnit != null) {
                showUnitTooltip(hoverUnit);
            }
        });
    }

    void showBuildingTooltip(Building build) {
        Table tooltip = new Table(Styles.black6);
        tooltip.margin(4f);
        
        tooltip.add("[accent]" + build.block.localizedName).row();
        tooltip.add("[lightgray]─────────────").row();
        
        // Power info
        if (build.block instanceof PowerGenerator gen) {
            tooltip.add("[stat]Power: [accent]" + (int)(gen.powerProduction * 60f) + "/s").left().row();
        }
        
        // Item capacity
        if (build.block.hasItems && build.block.itemCapacity > 0) {
            tooltip.add("[stat]Item Cap: [accent]" + build.block.itemCapacity).left().row();
            // Show current items
            if (build.items != null) {
                tooltip.add("[stat]Items: [accent]" + build.items.total() + "/" + build.block.itemCapacity).left().row();
            }
        }
        
        // Liquid capacity
        if (build.block.hasLiquids && build.block.liquidCapacity > 0) {
            tooltip.add("[stat]Liquid Cap: [accent]" + Strings.autoFixed(build.block.liquidCapacity, 1)).left().row();
        }
        
        // Health
        tooltip.add("[stat]Health: [accent]" + (int)build.health + "/" + (int)build.maxHealth).left().row();
        
        Vars.ui.showTooltip(tooltip);
    }

    void showUnitTooltip(Unit unit) {
        Table tooltip = new Table(Styles.black6);
        tooltip.margin(4f);
        
        tooltip.add("[accent]" + unit.type.localizedName).row();
        tooltip.add("[lightgray]─────────────").row();
        
        tooltip.add("[stat]Health: [accent]" + (int)unit.health + "/" + (int)unit.maxHealth).left().row();
        tooltip.add("[stat]Armor: [accent]" + (int)unit.type.armor).left().row();
        tooltip.add("[stat]Speed: [accent]" + Strings.autoFixed(unit.type.speed, 1)).left().row();
        
        if (unit.type.flying) {
            tooltip.add("[stat]Flying: [accent]Yes").left().row();
        }
        
        if (unit.type.mineSpeed > 0) {
            tooltip.add("[stat]Mine Speed: [accent]" + Strings.autoFixed(unit.type.mineSpeed, 1)).left().row();
        }
        
        if (unit.type.buildSpeed > 0) {
            tooltip.add("[stat]Build Speed: [accent]" + Strings.autoFixed(unit.type.buildSpeed, 1)).left().row();
        }
        
        Vars.ui.showTooltip(tooltip);
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