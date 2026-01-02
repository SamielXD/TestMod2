package tooltipsplus;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerGenerator;
import tooltipsplus.config.*;
import tooltipsplus.ui.*;
import tooltipsplus.util.ColorUtil;

public class TooltipsPlusMod extends Mod {
    
    private Settings settings;
    private ColorUtil colors;
    private TooltipRenderer tooltipRenderer;
    private VisualIndicators visualIndicators;
    private HealthDisplaySystem healthDisplay;
    private SettingsUI settingsUI;
    
    public TooltipsPlusMod() {
        settings = new Settings();
        colors = new ColorUtil();
        settings.load();
        colors.applyTheme(settings.colorTheme);
    }

    @Override
    public void init() {
        Log.info("TooltipsPlus v5.0 initializing...");
        
        if (settings.enabled) {
            tooltipRenderer = new TooltipRenderer(settings, colors);
            visualIndicators = new VisualIndicators(settings);
            healthDisplay = new HealthDisplaySystem(settings);
            
            setupTooltipSystem();
            setupVisualIndicators();
            setupHealthDisplay();
            setupHotkeys();
            injectStaticDescriptions();
        }
        
        settingsUI = new SettingsUI(settings, colors);
        settingsUI.build();
        
        Log.info("TooltipsPlus loaded with Helium-style health bars");
    }
    
    void setupTooltipSystem() {
        Events.run(EventType.Trigger.draw, () -> {
            if (tooltipRenderer != null) {
                tooltipRenderer.update();
            }
        });
    }
    
    void setupVisualIndicators() {
        Events.run(EventType.Trigger.draw, () -> {
            if (!settings.enabled || Vars.state.isMenu()) return;
            if (visualIndicators != null) {
                visualIndicators.update();
            }
        });
    }
    
    void setupHealthDisplay() {
        Events.run(EventType.Trigger.draw, () -> {
            if (!settings.enabled || Vars.state.isMenu()) return;
            if (healthDisplay != null) {
                healthDisplay.draw();
            }
        });
    }
    
    void setupHotkeys() {
        Events.run(EventType.Trigger.update, () -> {
            if (arc.Core.input.keyTap(arc.input.KeyCode.t)) {
                settings.enabled = !settings.enabled;
                settings.save();
                Vars.ui.showInfoToast(settings.enabled ? "[lime]Tooltips ON" : "[scarlet]Tooltips OFF", 2f);
            }
            
            if (arc.Core.input.keyTap(arc.input.KeyCode.p) && tooltipRenderer != null) {
                var lastBuilding = tooltipRenderer.getLastHoveredBuilding();
                if (lastBuilding != null) {
                    tooltipRenderer.setPinned(lastBuilding);
                }
            }
            
            if (arc.Core.input.keyTap(arc.input.KeyCode.r)) {
                settings.showRangeIndicators = !settings.showRangeIndicators;
                settings.save();
            }
            
            if (arc.Core.input.keyTap(arc.input.KeyCode.h)) {
                settings.showHealthBars = !settings.showHealthBars;
                settings.save();
            }
        });
    }
    
    void injectStaticDescriptions() {
        for (Block block : Vars.content.blocks()) {
            if (block.description != null && !block.description.contains("§")) {
                StringBuilder extra = new StringBuilder("\n[accent]§ Stats:");
                
                if (block instanceof PowerGenerator) {
                    PowerGenerator gen = (PowerGenerator)block;
                    extra.append("\n  Power: ").append((int)(gen.powerProduction * 60f)).append("/s");
                }
                if (block.hasItems && block.itemCapacity > 0) {
                    extra.append("\n  Items: ").append(block.itemCapacity);
                }
                if (block.health > 0) {
                    extra.append("\n  HP: ").append((int)block.health);
                }
                
                block.description += extra.toString();
            }
        }
    }
}