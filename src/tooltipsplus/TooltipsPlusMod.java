package tooltipsplus;

import arc.util.*;
import mindustry.*;
import mindustry.content.*;
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
        if (enabled) injectTooltips();
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

    void injectTooltips() {
        // Blocks
        for (Block block : Vars.content.blocks()) {
            block.buildVisibility = BuildVisibility.shown;
            // buildCost doesn't exist in newer versions - removed
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

        // Units
        for (UnitType unit : Vars.content.units()) {
            unit.description += "\n[accent]Tooltips+ Info:";
            unit.description += "\nHealth: " + (int)unit.health;
            unit.description += "\nArmor: " + (int)unit.armor;
            unit.description += "\nSpeed: " + Strings.autoFixed(unit.speed, 1);
            if (unit.flying) unit.description += "\nFlying: Yes";
            if (unit.mineSpeed > 0) unit.description += "\nMine Speed: " + Strings.autoFixed(unit.mineSpeed, 1);
            if (unit.buildSpeed > 0) unit.description += "\nBuild Speed: " + Strings.autoFixed(unit.buildSpeed, 1);
        }

        // Items
        for (Item item : Vars.content.items()) {
            item.description += "\n[accent]Tooltips+ Info:";
            if (showItemFlags) {
                if (item.flammability > 0) item.description += "\nFlammable: " + Strings.autoFixed(item.flammability, 2);
                if (item.explosiveness > 0) item.description += "\nExplosive: " + Strings.autoFixed(item.explosiveness, 2);
            }
        }

        // Liquids
        for (Liquid liquid : Vars.content.liquids()) {
            liquid.description += "\n[accent]Tooltips+ Info:";
            if (showTemperature) liquid.description += "\nTemp: " + Strings.autoFixed(liquid.temperature, 1);
            if (showItemFlags) {
                if (liquid.flammability > 0) liquid.description += "\nFlammable: " + Strings.autoFixed(liquid.flammability, 2);
                if (liquid.explosiveness > 0) liquid.description += "\nExplosive: " + Strings.autoFixed(liquid.explosiveness, 2);
            }
        }
    }

    void addSettingsUI() {
        try {
            Vars.ui.settings.addCategory("Tooltips+", Icon.book, table -> {
                table.row();
                table.checkPref("tooltipsplus-enabled", enabled, v -> {
                    enabled = v;
                    saveSettings();
                    Vars.ui.showInfo("[lime]" + (v ? "Enabled" : "Disabled") + " Tooltips+");
                    if (v) injectTooltips();
                });
                table.row();

                table.checkPref("tooltipsplus-temp", showTemperature, v -> {
                    showTemperature = v;
                    saveSettings();
                    Vars.ui.showInfo("[cyan]Temperature display: " + (v ? "ON" : "OFF"));
                });
                table.row();

                table.checkPref("tooltipsplus-itemflags", showItemFlags, v -> {
                    showItemFlags = v;
                    saveSettings();
                    Vars.ui.showInfo("[cyan]Item/Fluid flags: " + (v ? "ON" : "OFF"));
                });
                table.row();

                table.checkPref("tooltipsplus-debug", debug, v -> {
                    debug = v;
                    saveSettings();
                    Vars.ui.showInfo("[cyan]Debug mode: " + (v ? "ON" : "OFF"));
                });
                table.row();

                table.add("[lightgray]Extra info shown on block/unit/item dialogs.").padTop(8f).left();
            });
        } catch (Throwable ex) {
            Log.err("TooltipsPlus: failed to add settings UI", ex);
        }
    }
}