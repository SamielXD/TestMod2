package tooltipsplus;

import arc.util.*;
import mindustry.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;

public class TooltipsPlusMod extends Mod {
    
    boolean debug = true;
    
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
        if(!block.stats.initialized) {
            if(debug) Log.info("Skipped block @ (stats not initialized)", block.name);
            return false;
        }
        
        boolean changed = false;
        
        if(!block.stats.has(Stat.health) && block.health > 0) {
            block.stats.add(Stat.health, (int)block.health);
            if(debug) Log.info("Added health to @: @", block.name, (int)block.health);
            changed = true;
        }
        
        if(!block.stats.has(Stat.itemCapacity) && block.hasItems && block.itemCapacity > 0) {
            block.stats.add(Stat.itemCapacity, block.itemCapacity);
            if(debug) Log.info("Added itemCapacity to @: @", block.name, block.itemCapacity);
            changed = true;
        }
        
        if(!block.stats.has(Stat.liquidCapacity) && block.hasLiquids && block.liquidCapacity > 0) {
            block.stats.add(Stat.liquidCapacity, block.liquidCapacity, StatUnit.liquidUnits);
            if(debug) Log.info("Added liquidCapacity to @: @", block.name, fmt(block.liquidCapacity));
            changed = true;
        }
        
        if(block instanceof PowerGenerator gen && gen.powerProduction > 0f) {
            if(!block.stats.has(Stat.basePowerGeneration)) {
                float perSec = gen.powerProduction * 60f;
                block.stats.add(Stat.basePowerGeneration, perSec, StatUnit.powerSecond);
                if(debug) Log.info("Added power to @: @/s", block.name, fmt(perSec));
                changed = true;
            }
        }
        
        if(block.size > 1 && !block.stats.has(Stat.size)) {
            block.stats.add(Stat.size, block.size + "x" + block.size);
            if(debug) Log.info("Added size to @: @x@", block.name, block.size, block.size);
            changed = true;
        }
        
        if(block.requirements != null && block.requirements.length > 0) {
            int totalCost = 0;
            for(int i = 0; i < block.requirements.length; i++) {
                totalCost += block.requirements[i].amount;
            }
            if(totalCost > 50 && !block.stats.has(Stat.buildCost)) {
                block.stats.add(Stat.buildCost, totalCost);
                if(debug) Log.info("Added buildCost to @: @", block.name, totalCost);
                changed = true;
            }
        }
        
        return changed;
    }
    
    boolean enhanceUnit(UnitType unit) {
        if(!unit.stats.initialized) {
            if(debug) Log.info("Skipped unit @ (stats not initialized)", unit.name);
            return false;
        }
        
        boolean changed = false;
        
        if(!unit.stats.has(Stat.health)) {
            unit.stats.add(Stat.health, unit.health);
            if(debug) Log.info("Added health to @: @", unit.name, (int)unit.health);
            changed = true;
        }
        
        if(!unit.stats.has(Stat.speed)) {
            unit.stats.add(Stat.speed, unit.speed, StatUnit.tilesSecond);
            if(debug) Log.info("Added speed to @: @", unit.name, fmt(unit.speed));
            changed = true;
        }
        
        if(!unit.stats.has(Stat.armor)) {
            unit.stats.add(Stat.armor, unit.armor);
            if(debug) Log.info("Added armor to @: @", unit.name, (int)unit.armor);
            changed = true;
        }
        
        if(unit.flying && !unit.stats.has(Stat.flying)) {
            unit.stats.add(Stat.flying, "[accent]Yes[]");
            if(debug) Log.info("Added flying to @", unit.name);
            changed = true;
        }
        
        if(unit.mineSpeed > 0 && !unit.stats.has(Stat.mineSpeed)) {
            unit.stats.add(Stat.mineSpeed, unit.mineSpeed, StatUnit.perSecond);
            if(debug) Log.info("Added mineSpeed to @: @", unit.name, fmt(unit.mineSpeed));
            changed = true;
        }
        
        if(unit.buildSpeed > 0 && !unit.stats.has(Stat.buildSpeed)) {
            unit.stats.add(Stat.buildSpeed, unit.buildSpeed);
            if(debug) Log.info("Added buildSpeed to @: @", unit.name, fmt(unit.buildSpeed));
            changed = true;
        }
        
        return changed;
    }
    
    boolean enhanceItem(Item item) {
        if(!item.stats.initialized) {
            if(debug) Log.info("Skipped item @ (stats not initialized)", item.name);
            return false;
        }
        
        boolean changed = false;
        
        if(item.hardness > 0 && !item.stats.has(Stat.hardness)) {
            item.stats.add(Stat.hardness, item.hardness);
            if(debug) Log.info("Added hardness to @: @", item.name, item.hardness);
            changed = true;
        }
        
        if(item.flammability > 0.1f && !item.stats.has(Stat.flammability)) {
            item.stats.add(Stat.flammability, item.flammability);
            if(debug) Log.info("Added flammability to @: @", item.name, fmt(item.flammability));
            changed = true;
        }
        
        if(item.explosiveness > 0.1f && !item.stats.has(Stat.explosiveness)) {
            item.stats.add(Stat.explosiveness, item.explosiveness);
            if(debug) Log.info("Added explosiveness to @: @", item.name, fmt(item.explosiveness));
            changed = true;
        }
        
        if(item.radioactivity > 0.1f && !item.stats.has(Stat.radioactivity)) {
            item.stats.add(Stat.radioactivity, item.radioactivity);
            if(debug) Log.info("Added radioactivity to @: @", item.name, fmt(item.radioactivity));
            changed = true;
        }
        
        return changed;
    }
    
    boolean enhanceLiquid(Liquid liquid) {
        if(!liquid.stats.initialized) {
            if(debug) Log.info("Skipped liquid @ (stats not initialized)", liquid.name);
            return false;
        }
        
        boolean changed = false;
        
        if(!liquid.stats.has(Stat.temperature)) {
            liquid.stats.add(Stat.temperature, fmt(liquid.temperature));
            if(debug) Log.info("Added temperature to @: @", liquid.name, fmt(liquid.temperature));
            changed = true;
        }
        
        if(liquid.flammability > 0.1f && !liquid.stats.has(Stat.flammability)) {
            liquid.stats.add(Stat.flammability, liquid.flammability);
            if(debug) Log.info("Added flammability to @: @", liquid.name, fmt(liquid.flammability));
            changed = true;
        }
        
        if(liquid.explosiveness > 0.1f && !liquid.stats.has(Stat.explosiveness)) {
            liquid.stats.add(Stat.explosiveness, liquid.explosiveness);
            if(debug) Log.info("Added explosiveness to @: @", liquid.name, fmt(liquid.explosiveness));
            changed = true;
        }
        
        return changed;
    }
    
    String fmt(float v) {
        return Strings.autoFixed(v, 1);
    }
}