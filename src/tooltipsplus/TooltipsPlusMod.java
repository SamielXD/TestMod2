
package tooltipsplus;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;

public class TooltipsPlusMod extends Mod {
    
    public TooltipsPlusMod() {
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(this::injectTooltips);
        });
    }
    
    void injectTooltips() {
        Vars.content.blocks().each(this::enhanceBlock);
        Vars.content.units().each(this::enhanceUnit);
        Vars.content.items().each(this::enhanceItem);
        Vars.content.liquids().each(this::enhanceLiquid);
    }
    
    void enhanceBlock(Block block) {
        if(!block.stats.intialized) return;
        
        if(block.health > 0) {
            block.stats.add(Stat.health, block.health);
        }
        
        if(block.hasItems && block.itemCapacity > 0) {
            block.stats.add(Stat.itemCapacity, block.itemCapacity);
        }
        
        if(block.hasLiquids && block.liquidCapacity > 0) {
            block.stats.add(Stat.liquidCapacity, block.liquidCapacity, StatUnit.liquidUnits);
        }
        
        if(block.hasPower && block instanceof PowerGenerator gen) {
            block.stats.add(Stat.basePowerGeneration, gen.powerProduction * 60f, StatUnit.powerSecond);
        }
        
        if(block.size > 1) {
            block.stats.add(Stat.size, "@x@", block.size, block.size);
        }
        
        if(block.requirements != null && block.requirements.length > 0) {
            int totalCost = 0;
            for(int i = 0; i < block.requirements.length; i++) {
                totalCost += block.requirements[i].amount;
            }
            if(totalCost > 50) {
                block.stats.add(Stat.buildCost, totalCost);
            }
        }
    }
    
    void enhanceUnit(UnitType unit) {
        if(!unit.stats.intialized) return;
        
        unit.stats.add(Stat.health, unit.health);
        unit.stats.add(Stat.speed, unit.speed, StatUnit.tilesSecond);
        unit.stats.add(Stat.armor, unit.armor);
        
        if(unit.flying) {
            unit.stats.add(Stat.flying, "[accent]Yes[]");
        }
        
        if(unit.naval) {
            unit.stats.add(Stat.abilities, "[accent]Naval[]");
        }
        
        if(unit.mineSpeed > 0) {
            unit.stats.add(Stat.mineSpeed, unit.mineSpeed, StatUnit.perSecond);
        }
        
        if(unit.buildSpeed > 0) {
            unit.stats.add(Stat.buildSpeed, unit.buildSpeed);
        }
    }
    
    void enhanceItem(Item item) {
        if(!item.stats.intialized) return;
        
        if(item.hardness > 0) {
            item.stats.add(Stat.hardness, item.hardness);
        }
        
        if(item.flammability > 0.1f) {
            item.stats.add(Stat.flammability, item.flammability);
        }
        
        if(item.explosiveness > 0.1f) {
            item.stats.add(Stat.explosiveness, item.explosiveness);
        }
        
        if(item.radioactivity > 0.1f) {
            item.stats.add(Stat.radioactivity, item.radioactivity);
        }
    }
    
    void enhanceLiquid(Liquid liquid) {
        if(!liquid.stats.intialized) return;
        
        item.stats.add(Stat.temperature, liquid.temperature);
        
        if(liquid.flammability > 0.1f) {
            liquid.stats.add(Stat.flammability, liquid.flammability);
        }
        
        if(liquid.explosiveness > 0.1f) {
            liquid.stats.add(Stat.explosiveness, liquid.explosiveness);
        }
    }
}