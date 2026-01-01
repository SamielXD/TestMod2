package tooltipsplus;

import arc.util.*;
import mindustry.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;

public class TooltipsPlusMod extends Mod {
    
    boolean debug = false;
    
    @Override
    public void init() {
        Log.info("ToolTips+ initializing...");
        injectTooltips();
        Log.info("ToolTips+ loaded successfully");
    }
    
    void injectTooltips() {
        int blocks = 0, units = 0, items = 0, liquids = 0;
        
        for(Block block : Vars.content.blocks()) {
            if(enhanceBlock(block)) blocks++;
        }
        
        for(UnitType unit : Vars.content.units()) {
            if(enhanceUnit(unit)) units++;
        }
        
        for(Item item : Vars.content.items()) {
            if(enhanceItem(item)) items++;
        }
        
        for(Liquid liquid : Vars.content.liquids()) {
            if(enhanceLiquid(liquid)) liquids++;
        }
        
        Log.info("ToolTips+ enhanced: @ blocks, @ units, @ items, @ liquids", blocks, units, items, liquids);
    }
    
    boolean enhanceBlock(Block block) {
        try {
            boolean changed = false;
            
            if(block.health > 0) {
                block.stats.add(Stat.health, (int)block.health);
                if(debug) Log.info("Added health to @: @", block.name, (int)block.health);
                changed = true;
            }
            
            if(block.hasItems && block.itemCapacity > 0) {
                block.stats.add(Stat.itemCapacity, block.itemCapacity);
                if(debug) Log.info("Added itemCapacity to @: @", block.name, block.itemCapacity);
                changed = true;
            }
            
            if(block.hasLiquids && block.liquidCapacity > 0) {
                block.stats.add(Stat.liquidCapacity, block.liquidCapacity, StatUnit.liquidUnits);
                if(debug) Log.info("Added liquidCapacity to @: @", block.name, fmt(block.liquidCapacity));
                changed = true;
            }
            
            if(block instanceof PowerGenerator gen && gen.powerProduction > 0f) {
                float perSec = gen.powerProduction * 60f;
                block.stats.add(Stat.basePowerGeneration, perSec, StatUnit.powerSecond);
                if(debug) Log.info("Added power to @: @/s", block.name, fmt(perSec));
                changed = true;
            }
            
            if(block.size > 1) {
                block.stats.add(Stat.size, block.size + "x" + block.size);
                if(debug) Log.info("Added size to @: @x@", block.name, block.size, block.size);
                changed = true;
            }
            
            if(block.requirements != null && block.requirements.length > 0) {
                int totalCost = 0;
                for(int i = 0; i < block.requirements.length; i++) {
                    totalCost += block.requirements[i].amount;
                }
                if(totalCost > 50) {
                    block.stats.add(Stat.buildCost, totalCost);
                    if(debug) Log.info("Added buildCost to @: @", block.name, totalCost);
                    changed = true;
                }
            }
            
            return changed;
        } catch(Exception e) {
            if(debug) Log.err("Error enhancing block @", block.name, e);
            return false;
        }
    }
    
    boolean enhanceUnit(UnitType unit) {
        try {
            boolean changed = false;
            
            unit.stats.add(Stat.health, unit.health);
            if(debug) Log.info("Added health to @: @", unit.name, (int)unit.health);
            changed = true;
            
            unit.stats.add(Stat.speed, unit.speed, StatUnit.tilesSecond);
            if(debug) Log.info("Added speed to @: @", unit.name, fmt(unit.speed));
            changed = true;
            
            unit.stats.add(Stat.armor, unit.armor);
            if(debug) Log.info("Added armor to @: @", unit.name, (int)unit.armor);
            changed = true;
            
            if(unit.flying) {
                unit.stats.add(Stat.flying, "[accent]Yes[]");
                if(debug) Log.info("Added flying to @", unit.name);
                changed = true;
            }
            
            if(unit.mineSpeed > 0) {
                unit.stats.add(Stat.mineSpeed, unit.mineSpeed, StatUnit.perSecond);
                if(debug) Log.info("Added mineSpeed to @: @", unit.name, fmt(unit.mineSpeed));
                changed = true;
            }
            
            if(unit.buildSpeed > 0) {
                unit.stats.add(Stat.buildSpeed, unit.buildSpeed);
                if(debug) Log.info("Added buildSpeed to @: @", unit.name, fmt(unit.buildSpeed));
                changed = true;
            }
            
            return changed;
        } catch(Exception e) {
            if(debug) Log.err("Error enhancing unit @", unit.name, e);
            return false;
        }
    }
    
    boolean enhanceItem(Item item) {
        try {
            boolean changed = false;
            
            if(item.hardness > 0) {
                item.stats.add(Stat.input, "[lightgray]Hardness: " + item.hardness + "[]");
                if(debug) Log.info("Added hardness to @: @", item.name, item.hardness);
                changed = true;
            }
            
            if(item.flammability > 0.1f) {
                item.stats.add(Stat.flammability, item.flammability);
                if(debug) Log.info("Added flammability to @: @", item.name, fmt(item.flammability));
                changed = true;
            }
            
            if(item.explosiveness > 0.1f) {
                item.stats.add(Stat.explosiveness, item.explosiveness);
                if(debug) Log.info("Added explosiveness to @: @", item.name, fmt(item.explosiveness));
                changed = true;
            }
            
            if(item.radioactivity > 0.1f) {
                item.stats.add(Stat.input, "[green]Radioactive: " + fmt(item.radioactivity) + "[]");
                if(debug) Log.info("Added radioactivity to @: @", item.name, fmt(item.radioactivity));
                changed = true;
            }
            
            return changed;
        } catch(Exception e) {
            if(debug) Log.err("Error enhancing item @", item.name, e);
            return false;
        }
    }
    
    boolean enhanceLiquid(Liquid liquid) {
        try {
            boolean changed = false;
            
            liquid.stats.add(Stat.input, "[lightgray]Temp: " + fmt(liquid.temperature) + "[]");
            if(debug) Log.info("Added temperature to @: @", liquid.name, fmt(liquid.temperature));
            changed = true;
            
            if(liquid.flammability > 0.1f) {
                liquid.stats.add(Stat.flammability, liquid.flammability);
                if(debug) Log.info("Added flammability to @: @", liquid.name, fmt(liquid.flammability));
                changed = true;
            }
            
            if(liquid.explosiveness > 0.1f) {
                liquid.stats.add(Stat.explosiveness, liquid.explosiveness);
                if(debug) Log.info("Added explosiveness to @: @", liquid.name, fmt(liquid.explosiveness));
                changed = true;
            }
            
            return changed;
        } catch(Exception e) {
            if(debug) Log.err("Error enhancing liquid @", liquid.name, e);
            return false;
        }
    }
    
    String fmt(float v) {
        return Strings.autoFixed(v, 1);
    }
}