package tooltipsplus;

import arc.Core;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

public class TooltipsPlusMod extends Mod {

    boolean enabled;
    boolean debug;

    public TooltipsPlusMod(){
        // constructor only
    }

    @Override
    public void init(){
        loadSettings();

        if(!enabled){
            Log.info("Tooltips+ disabled via settings");
            return;
        }

        Log.info("Tooltips+ injecting stats...");
        inject();
        Log.info("Tooltips+ loaded");
    }

    void loadSettings(){
        enabled = Core.settings.getBool("tooltipsplus-enabled", true);
        debug   = Core.settings.getBool("tooltipsplus-debug", false);
    }

    void inject(){
        for(Block b : Vars.content.blocks()){
            try{
                enhanceBlock(b);
            }catch(Throwable t){
                if(debug) Log.err("Block failed: " + b.name, t);
            }
        }

        for(UnitType u : Vars.content.units()){
            try{
                enhanceUnit(u);
            }catch(Throwable t){
                if(debug) Log.err("Unit failed: " + u.name, t);
            }
        }

        for(Item i : Vars.content.items()){
            try{
                enhanceItem(i);
            }catch(Throwable t){
                if(debug) Log.err("Item failed: " + i.name, t);
            }
        }

        for(Liquid l : Vars.content.liquids()){
            try{
                enhanceLiquid(l);
            }catch(Throwable t){
                if(debug) Log.err("Liquid failed: " + l.name, t);
            }
        }
    }

    void enhanceBlock(Block b){
        if(b.health > 0){
            b.stats.add(Stat.health, (int)b.health);
        }

        if(b.itemCapacity > 0){
            b.stats.add(Stat.itemCapacity, b.itemCapacity);
        }

        if(b.liquidCapacity > 0){
            b.stats.add(Stat.liquidCapacity, b.liquidCapacity, StatUnit.liquidUnits);
        }

        if(b instanceof PowerGenerator g && g.powerProduction > 0){
            b.stats.add(
                Stat.basePowerGeneration,
                g.powerProduction * 60f,
                StatUnit.powerSecond
            );
        }

        if(b.size > 1){
            b.stats.add(
                Stat.input,
                "[lightgray]Size:[] " + b.size + "x" + b.size
            );
        }
    }

    void enhanceUnit(UnitType u){
        u.stats.add(Stat.health, (int)u.health);
        u.stats.add(Stat.speed, u.speed, StatUnit.tilesSecond);
        u.stats.add(Stat.armor, (int)u.armor);

        if(u.flying){
            u.stats.add(Stat.input, "[accent]Flying[]");
        }

        if(u.mineSpeed > 0){
            u.stats.add(Stat.mineSpeed, u.mineSpeed, StatUnit.perSecond);
        }

        if(u.buildSpeed > 0){
            u.stats.add(Stat.buildSpeed, u.buildSpeed);
        }
    }

    void enhanceItem(Item i){
        if(i.hardness > 0){
            i.stats.add(
                Stat.input,
                "[lightgray]Hardness:[] " + i.hardness
            );
        }

        if(i.flammability > 0){
            i.stats.add(
                Stat.input,
                "[orange]Flammable[] " + Strings.autoFixed(i.flammability, 2)
            );
        }

        if(i.explosiveness > 0){
            i.stats.add(
                Stat.input,
                "[scarlet]Explosive[] " + Strings.autoFixed(i.explosiveness, 2)
            );
        }

        if(i.radioactivity > 0){
            i.stats.add(
                Stat.input,
                "[green]Radioactive[] " + Strings.autoFixed(i.radioactivity, 2)
            );
        }
    }

    void enhanceLiquid(Liquid l){
        l.stats.add(
            Stat.input,
            "[lightgray]Temp:[] " + Strings.autoFixed(l.temperature, 2)
        );

        if(l.flammability > 0){
            l.stats.add(
                Stat.input,
                "[orange]Flammable[] " + Strings.autoFixed(l.flammability, 2)
            );
        }

        if(l.explosiveness > 0){
            l.stats.add(
                Stat.input,
                "[scarlet]Explosive[] " + Strings.autoFixed(l.explosiveness, 2)
            );
        }
    }
}