package tooltipsplus;

import arc.*;
import arc.graphics.*;
import arc.util.*;
import mindustry.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;
import mindustry.game.EventType.*;

public class TooltipsPlusMod extends Mod{

    // --------------------
    // SETTINGS VALUES
    // --------------------
    boolean enabled;
    boolean blocks;
    boolean units;
    boolean items;
    boolean liquids;
    boolean showToast;
    boolean debug;

    // --------------------
    // SETTINGS KEYS
    // --------------------
    static final String K_ENABLED = "tp-enabled";
    static final String K_BLOCKS = "tp-blocks";
    static final String K_UNITS = "tp-units";
    static final String K_ITEMS = "tp-items";
    static final String K_LIQUIDS = "tp-liquids";
    static final String K_TOAST = "tp-toast";
    static final String K_DEBUG = "tp-debug";

    public TooltipsPlusMod(){
        Events.on(ClientLoadEvent.class, e -> {
            loadSettings();

            if(enabled){
                injectAll();
            }

            Core.app.post(this::addSettingsUI);

            if(showToast){
                Vars.ui.showInfoToast("[lime]Tooltips+ loaded", 2f);
            }
        });
    }

    // --------------------
    // LOAD / SAVE SETTINGS
    // --------------------
    void loadSettings(){
        enabled  = Core.settings.getBool(K_ENABLED, true);
        blocks   = Core.settings.getBool(K_BLOCKS, true);
        units    = Core.settings.getBool(K_UNITS, true);
        items    = Core.settings.getBool(K_ITEMS, true);
        liquids  = Core.settings.getBool(K_LIQUIDS, true);
        showToast= Core.settings.getBool(K_TOAST, true);
        debug    = Core.settings.getBool(K_DEBUG, false);
    }

    void save(){
        Core.settings.put(K_ENABLED, enabled);
        Core.settings.put(K_BLOCKS, blocks);
        Core.settings.put(K_UNITS, units);
        Core.settings.put(K_ITEMS, items);
        Core.settings.put(K_LIQUIDS, liquids);
        Core.settings.put(K_TOAST, showToast);
        Core.settings.put(K_DEBUG, debug);
        Core.settings.forceSave();
    }

    // --------------------
    // INJECT TOOLTIP DATA
    // --------------------
    void injectAll(){
        if(blocks)  Vars.content.blocks().each(this::enhanceBlock);
        if(units)   Vars.content.units().each(this::enhanceUnit);
        if(items)   Vars.content.items().each(this::enhanceItem);
        if(liquids) Vars.content.liquids().each(this::enhanceLiquid);

        if(debug) Log.info("Tooltips+ injected");
    }

    // --------------------
    // BLOCKS
    // --------------------
    void enhanceBlock(Block b){
        if(!b.stats.initialized) return;

        if(!b.stats.has(Stat.health) && b.health > 0){
            b.stats.add(Stat.health, b.health);
        }

        if(b.hasItems && !b.stats.has(Stat.itemCapacity)){
            b.stats.add(Stat.itemCapacity, b.itemCapacity);
        }

        if(b.hasLiquids && !b.stats.has(Stat.liquidCapacity)){
            b.stats.add(Stat.liquidCapacity, b.liquidCapacity, StatUnit.liquidUnits);
        }

        if(b instanceof PowerGenerator g && g.powerProduction > 0){
            if(!b.stats.has(Stat.basePowerGeneration)){
                b.stats.add(Stat.basePowerGeneration, g.powerProduction * 60f, StatUnit.powerSecond);
            }
        }

        if(!b.stats.has(Stat.size)){
            b.stats.add(Stat.size, b.size + "x" + b.size);
        }
    }

    // --------------------
    // UNITS
    // --------------------
    void enhanceUnit(UnitType u){
        if(!u.stats.initialized) return;

        if(!u.stats.has(Stat.health)){
            u.stats.add(Stat.health, u.health);
        }

        if(!u.stats.has(Stat.speed)){
            u.stats.add(Stat.speed, u.speed, StatUnit.tilesSecond);
        }

        if(!u.stats.has(Stat.armor)){
            u.stats.add(Stat.armor, u.armor);
        }

        if(u.flying && !u.stats.has(Stat.flying)){
            u.stats.add(Stat.flying, "[accent]Yes");
        }

        if(u.mineSpeed > 0 && !u.stats.has(Stat.mineSpeed)){
            u.stats.add(Stat.mineSpeed, u.mineSpeed, StatUnit.perSecond);
        }

        if(u.buildSpeed > 0 && !u.stats.has(Stat.buildSpeed)){
            u.stats.add(Stat.buildSpeed, u.buildSpeed);
        }
    }

    // --------------------
    // ITEMS
    // --------------------
    void enhanceItem(Item i){
        if(!i.stats.initialized) return;

        if(i.hardness > 0 && !i.stats.has(Stat.hardness)){
            i.stats.add(Stat.hardness, i.hardness);
        }

        if(i.flammability > 0 && !i.stats.has(Stat.flammability)){
            i.stats.add(Stat.flammability, i.flammability);
        }

        if(i.explosiveness > 0 && !i.stats.has(Stat.explosiveness)){
            i.stats.add(Stat.explosiveness, i.explosiveness);
        }

        if(i.radioactivity > 0 && !i.stats.has(Stat.radioactivity)){
            i.stats.add(Stat.radioactivity, i.radioactivity);
        }
    }

    // --------------------
    // LIQUIDS
    // --------------------
    void enhanceLiquid(Liquid l){
        if(!l.stats.initialized) return;

        if(!l.stats.has(Stat.temperature)){
            l.stats.add(Stat.temperature, l.temperature);
        }

        if(l.flammability > 0 && !l.stats.has(Stat.flammability)){
            l.stats.add(Stat.flammability, l.flammability);
        }

        if(l.explosiveness > 0 && !l.stats.has(Stat.explosiveness)){
            l.stats.add(Stat.explosiveness, l.explosiveness);
        }
    }

    // --------------------
    // SETTINGS UI
    // --------------------
    void addSettingsUI(){
        Vars.ui.settings.addCategory("Tooltips+", Icon.book, t -> {

            t.add("[accent]Tooltips+").pad(10).row();
            t.image().color(Color.gray).height(2).width(400).pad(5).row();

            t.checkPref(K_ENABLED, enabled, v -> {
                enabled = v;
                save();
            }).row();

            t.add("[lightgray]Content").padTop(6).row();

            t.checkPref(K_BLOCKS, blocks, v -> { blocks = v; save(); }).row();
            t.checkPref(K_UNITS, units, v -> { units = v; save(); }).row();
            t.checkPref(K_ITEMS, items, v -> { items = v; save(); }).row();
            t.checkPref(K_LIQUIDS, liquids, v -> { liquids = v; save(); }).row();

            t.image().color(Color.gray).height(2).width(400).pad(6).row();

            t.checkPref(K_TOAST, showToast, v -> { showToast = v; save(); }).row();
            t.checkPref(K_DEBUG, debug, v -> { debug = v; save(); }).row();

            t.image().color(Color.gray).height(2).width(400).pad(6).row();

            t.button("[cyan]Re-apply tooltips", () -> {
                if(enabled){
                    injectAll();
                    Vars.ui.showInfoToast("[lime]Tooltips updated", 2f);
                }else{
                    Vars.ui.showInfoToast("[scarlet]Tooltips+ disabled", 2f);
                }
            }).size(280, 55).pad(6).row();

            t.add("[gray]Restart required to remove injected stats").padTop(6).row();
        });
    }
}