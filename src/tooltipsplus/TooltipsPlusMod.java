package tooltipsplus;

import arc.*;
import arc.func.Cons;
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
import mindustry.gen.*;

public class TooltipsPlusMod extends Mod {
    
    public TooltipsPlusMod() {
        Log.info("ToolTips+ loading...");
        
        Events.on(ClientLoadEvent.class, e -> {
            Log.info("ToolTips+ applying enhancements...");
            Core.app.post(() -> {
                setupTooltips();
                Log.info("ToolTips+ loaded successfully!");
            });
        });
    }
    
    void setupTooltips() {
        Vars.content.blocks().each(block -> {
            try {
                enhanceBlockTooltip(block);
            } catch(Exception e) {
                Log.err("Failed to enhance block: " + block.name, e);
            }
        });
        
        Vars.content.units().each(unit -> {
            try {
                enhanceUnitTooltip(unit);
            } catch(Exception e) {
                Log.err("Failed to enhance unit: " + unit.name, e);
            }
        });
        
        Vars.content.items().each(item -> {
            try {
                enhanceItemTooltip(item);
            } catch(Exception e) {
                Log.err("Failed to enhance item: " + item.name, e);
            }
        });
        
        Vars.content.liquids().each(liquid -> {
            try {
                enhanceLiquidTooltip(liquid);
            } catch(Exception e) {
                Log.err("Failed to enhance liquid: " + liquid.name, e);
            }
        });
    }
    
    void enhanceBlockTooltip(Block block) {
        if(block.description == null) block.description = "";
        
        StringBuilder enhanced = new StringBuilder();
        enhanced.append(block.description);
        
        if(enhanced.length() > 0) {
            enhanced.append("\n");
        }
        
        enhanced.append("\n[stat]Health:[] ").append((int)block.health);
        
        if(block.hasItems && block.itemCapacity > 0) {
            enhanced.append("\n[stat]Items:[] ").append(block.itemCapacity);
        }
        
        if(block.hasLiquids && block.liquidCapacity > 0) {
            enhanced.append("\n[stat]Liquids:[] ").append((int)block.liquidCapacity);
        }
        
        if(block.hasPower && block instanceof PowerGenerator gen) {
            enhanced.append("\n[stat]Power:[] +").append(Strings.autoFixed(gen.powerProduction * 60f, 1)).append("/s");
        }
        
        if(block.requirements != null && block.requirements.length > 0) {
            int totalCost = 0;
            for(int i = 0; i < block.requirements.length; i++) {
                totalCost += block.requirements[i].amount;
            }
            if(totalCost > 50) {
                enhanced.append("\n[stat]Cost:[] ").append(totalCost);
            }
        }
        
        if(block.size > 1) {
            enhanced.append("\n[stat]Size:[] ").append(block.size).append("x").append(block.size);
        }
        
        block.description = enhanced.toString();
        block.details = enhanced.toString();
    }
    
    void enhanceUnitTooltip(UnitType unit) {
        if(unit.description == null) unit.description = "";
        
        StringBuilder enhanced = new StringBuilder();
        enhanced.append(unit.description);
        
        if(enhanced.length() > 0) {
            enhanced.append("\n");
        }
        
        enhanced.append("\n[stat]Health:[] ").append((int)unit.health);
        enhanced.append("\n[stat]Speed:[] ").append(Strings.autoFixed(unit.speed, 1));
        enhanced.append("\n[stat]Armor:[] ").append((int)unit.armor);
        
        if(unit.flying) {
            enhanced.append("\n[accent]Flying[]");
        } else if(unit.naval) {
            enhanced.append("\n[accent]Naval[]");
        }
        
        if(unit.mineSpeed > 0) {
            enhanced.append("\n[accent]Can mine[]");
        }
        
        if(unit.buildSpeed > 0) {
            enhanced.append("\n[accent]Can build[]");
        }
        
        unit.description = enhanced.toString();
        unit.details = enhanced.toString();
    }
    
    void enhanceItemTooltip(Item item) {
        if(item.description == null) item.description = "";
        
        StringBuilder enhanced = new StringBuilder();
        enhanced.append(item.description);
        
        if(enhanced.length() > 0) {
            enhanced.append("\n");
        }
        
        if(item.hardness > 0) {
            enhanced.append("\n[stat]Hardness:[] ").append(item.hardness);
        }
        
        if(item.flammability > 0.1f) {
            enhanced.append("\n[orange]Flammable[]");
        }
        
        if(item.explosiveness > 0.1f) {
            enhanced.append("\n[scarlet]Explosive[]");
        }
        
        if(item.radioactivity > 0.1f) {
            enhanced.append("\n[green]Radioactive[]");
        }
        
        item.description = enhanced.toString();
        item.details = enhanced.toString();
    }
    
    void enhanceLiquidTooltip(Liquid liquid) {
        if(liquid.description == null) liquid.description = "";
        
        StringBuilder enhanced = new StringBuilder();
        enhanced.append(liquid.description);
        
        if(enhanced.length() > 0) {
            enhanced.append("\n");
        }
        
        if(liquid.temperature > 0.7f) {
            enhanced.append("\n[orange]Hot[]");
        } else if(liquid.temperature < 0.3f) {
            enhanced.append("\n[cyan]Cold[]");
        }
        
        if(liquid.flammability > 0.1f) {
            enhanced.append("\n[orange]Flammable[]");
        }
        
        if(liquid.explosiveness > 0.1f) {
            enhanced.append("\n[scarlet]Explosive[]");
        }
        
        liquid.description = enhanced.toString();
        liquid.details = enhanced.toString();
    }
}