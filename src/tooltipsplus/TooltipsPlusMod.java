package tooltipsplus;

import arc.*;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.defense.*;

public class TooltipsPlusMod extends Mod {
    
    public TooltipsPlusMod() {
        Events.on(ClientLoadEvent.class, e -> {
            setupTooltips();
        });
    }
    
    void setupTooltips() {
        Vars.content.blocks().each(block -> {
            enhanceBlockTooltip(block);
        });
        
        Vars.content.units().each(unit -> {
            enhanceUnitTooltip(unit);
        });
        
        Vars.content.items().each(item -> {
            enhanceItemTooltip(item);
        });
        
        Vars.content.liquids().each(liquid -> {
            enhanceLiquidTooltip(liquid);
        });
    }
    
    void enhanceBlockTooltip(Block block) {
        String original = block.description;
        StringBuilder enhanced = new StringBuilder(original);
        
        enhanced.append("\n\n[lightgray]");
        enhanced.append("Health: ").append((int)block.health);
        
        if(block.hasItems && block.itemCapacity > 0) {
            enhanced.append("\nItem capacity: ").append(block.itemCapacity);
        }
        
        if(block.hasLiquids && block.liquidCapacity > 0) {
            enhanced.append("\nLiquid capacity: ").append((int)block.liquidCapacity);
        }
        
        if(block.hasPower) {
            if(block.consumes.hasPower()) {
                float usage = block.consumes.getPower().usage * 60f;
                enhanced.append("\nPower: -").append(Strings.autoFixed(usage, 2)).append("/s");
            }
            if(block instanceof PowerGenerator gen) {
                enhanced.append("\nPower: +").append(Strings.autoFixed(gen.powerProduction * 60f, 2)).append("/s");
            }
        }
        
        if(block.requirements.length > 0 && block.buildCost > 100) {
            enhanced.append("\nBuild time: ").append(Strings.autoFixed(block.buildCost / 60f, 1)).append("s");
        }
        
        if(block.size > 1) {
            enhanced.append("\nSize: ").append(block.size).append("x").append(block.size);
        }
        
        enhanced.append("[]");
        block.description = enhanced.toString();
    }
    
    void enhanceUnitTooltip(UnitType unit) {
        String original = unit.description;
        StringBuilder enhanced = new StringBuilder(original);
        
        enhanced.append("\n\n[lightgray]");
        enhanced.append("Health: ").append((int)unit.health);
        enhanced.append("\nSpeed: ").append(Strings.autoFixed(unit.speed * 60f / 8f, 1)).append(" tiles/s");
        enhanced.append("\nArmor: ").append((int)unit.armor);
        
        if(unit.flying) {
            enhanced.append("\nType: Flying");
        } else if(unit.naval) {
            enhanced.append("\nType: Naval");
        } else {
            enhanced.append("\nType: Ground");
        }
        
        if(unit.mineSpeed > 0) {
            enhanced.append("\nCan mine");
        }
        
        if(unit.buildSpeed > 0) {
            enhanced.append("\nCan build");
        }
        
        enhanced.append("[]");
        unit.description = enhanced.toString();
    }
    
    void enhanceItemTooltip(Item item) {
        String original = item.description;
        StringBuilder enhanced = new StringBuilder(original);
        
        enhanced.append("\n\n[lightgray]");
        
        if(item.hardness > 0) {
            enhanced.append("Hardness: ").append(item.hardness);
        }
        
        if(item.flammability > 0.1f) {
            enhanced.append("\n[orange]Flammable");
        }
        
        if(item.explosiveness > 0.1f) {
            enhanced.append("\n[scarlet]Explosive");
        }
        
        if(item.radioactivity > 0.1f) {
            enhanced.append("\n[green]Radioactive");
        }
        
        enhanced.append("[]");
        item.description = enhanced.toString();
    }
    
    void enhanceLiquidTooltip(Liquid liquid) {
        String original = liquid.description;
        StringBuilder enhanced = new StringBuilder(original);
        
        enhanced.append("\n\n[lightgray]");
        
        if(liquid.temperature > 0.7f) {
            enhanced.append("Temperature: Hot");
        } else if(liquid.temperature < 0.3f) {
            enhanced.append("Temperature: Cold");
        }
        
        if(liquid.flammability > 0.1f) {
            enhanced.append("\n[orange]Flammable");
        }
        
        if(liquid.explosiveness > 0.1f) {
            enhanced.append("\n[scarlet]Explosive");
        }
        
        enhanced.append("[]");
        liquid.description = enhanced.toString();
    }
}
