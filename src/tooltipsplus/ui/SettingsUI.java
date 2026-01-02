package tooltipsplus.ui;

import arc.scene.ui.layout.Table;
import arc.util.*;
import mindustry.Vars;
import mindustry.ui.dialogs.*;
import mindustry.gen.Icon;
import tooltipsplus.config.Settings;
import tooltipsplus.util.*;

public class SettingsUI {
    private Settings settings;
    private ColorUtil colors;
    
    public SettingsUI(Settings settings, ColorUtil colors) {
        this.settings = settings;
        this.colors = colors;
    }
    
    public void build() {
        try {
            Vars.ui.settings.addCategory("Tooltips+", Icon.book, t -> {
                t.defaults().left().padTop(4f);
                
                t.add("[accent]" + FormatUtil.repeat("═", 15) + " Main " + FormatUtil.repeat("═", 15)).center().colspan(2).padBottom(8f).row();
                
                t.check("Enable Tooltips+", settings.enabled, v -> {
                    settings.enabled = v;
                    settings.save();
                }).colspan(2).left().row();
                
                t.add("[lightgray]Keys: T=Toggle | P=Pin | R=Range | H=Health").colspan(2).left().padTop(4f).row();
                
                t.add("[accent]" + FormatUtil.repeat("═", 13) + " Visual UI " + FormatUtil.repeat("═", 13)).center().colspan(2).padTop(12f).padBottom(8f).row();
                
                addVisualSettings(t);
                addDisplaySettings(t);
                addFeatureSettings(t);
                addPresetsAndInfo(t);
            });
        } catch (Throwable ex) {
            Log.err("TooltipsPlus: Failed to add settings UI", ex);
        }
    }
    
    void addVisualSettings(Table t) {
        t.check("Health Bars", settings.showHealthBars, v -> {
            settings.showHealthBars = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Shield Stacks", settings.showShieldStacks, v -> {
            settings.showShieldStacks = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Range Indicators", settings.showRangeIndicators, v -> {
            settings.showRangeIndicators = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Vision Cones (Turrets)", settings.showVisionCones, v -> {
            settings.showVisionCones = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Effect Ranges", settings.showEffectRanges, v -> {
            settings.showEffectRanges = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Animate Ranges", settings.animateRanges, v -> {
            settings.animateRanges = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Show Unit Ranges", settings.showUnitRanges, v -> {
            settings.showUnitRanges = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Show Team Ranges", settings.showTeamRanges, v -> {
            settings.showTeamRanges = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Ore Tooltips", settings.showOreTooltips, v -> {
            settings.showOreTooltips = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.add("Range Opacity: ").left();
        t.slider(0, 100, 5, (int)(settings.rangeOpacity * 100), v -> {
            settings.rangeOpacity = v / 100f;
            settings.save();
        }).width(200f).row();
        
        t.add("[lightgray](" + (int)(settings.rangeOpacity * 100) + "%)").colspan(2).left().padTop(-4f).row();
        
        t.add("Effect Opacity: ").left();
        t.slider(0, 100, 5, (int)(settings.effectRangeOpacity * 100), v -> {
            settings.effectRangeOpacity = v / 100f;
            settings.save();
        }).width(200f).row();
        
        t.add("[lightgray](" + (int)(settings.effectRangeOpacity * 100) + "%)").colspan(2).left().padTop(-4f).row();
    }
    
    void addDisplaySettings(Table t) {
        t.add("[accent]" + FormatUtil.repeat("═", 14) + " Display " + FormatUtil.repeat("═", 14)).center().colspan(2).padTop(12f).padBottom(8f).row();
        
        t.check("Compact Mode", settings.compactMode, v -> {
            settings.compactMode = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Follow Cursor", settings.followCursor, v -> {
            settings.followCursor = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Show Icons", settings.showIcons, v -> {
            settings.showIcons = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Highlight Hovered", settings.highlightHovered, v -> {
            settings.highlightHovered = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Hover Sound", settings.playHoverSound, v -> {
            settings.playHoverSound = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.add("Color Theme: ").left();
        t.button(settings.colorTheme, () -> {
            String[] themes = {"default", "dark", "neon", "minimal"};
            int index = 0;
            for (int i = 0; i < themes.length; i++) {
                if (themes[i].equals(settings.colorTheme)) {
                    index = (i + 1) % themes.length;
                    break;
                }
            }
            settings.colorTheme = themes[index];
            colors.applyTheme(settings.colorTheme);
            settings.save();
        }).width(150f).row();
        
        t.add("Tooltip Opacity: ").left();
        t.slider(0, 10, 1, settings.tooltipOpacity, v -> {
            settings.tooltipOpacity = (int)v;
            settings.save();
        }).width(200f).row();
        
        t.add("Hover Delay: ").left();
        t.slider(0f, 1f, 0.05f, settings.hoverDelay, v -> {
            settings.hoverDelay = v;
            settings.save();
        }).width(200f).row();
        
        t.add("[lightgray](" + Strings.autoFixed(settings.hoverDelay, 2) + "s)").colspan(2).left().padTop(-4f).row();
        
        t.add("Font Size: ").left();
        t.slider(0, 2, 1, settings.fontSize, v -> {
            settings.fontSize = (int)v;
            settings.save();
        }).width(200f).row();
        
        String[] fontLabels = {"Small", "Normal", "Large"};
        t.add("[lightgray]" + fontLabels[settings.fontSize]).colspan(2).left().padTop(-4f).row();
    }
    
    void addFeatureSettings(Table t) {
        t.add("[accent]" + FormatUtil.repeat("═", 13) + " Features " + FormatUtil.repeat("═", 13)).center().colspan(2).padTop(12f).padBottom(8f).row();
        
        t.check("Power Details", settings.showPowerDetails, v -> {
            settings.showPowerDetails = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Item Flow Rates", settings.showItemFlow, v -> {
            settings.showItemFlow = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Advanced Unit Info", settings.showUnitAdvanced, v -> {
            settings.showUnitAdvanced = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Show Warnings", settings.showWarnings, v -> {
            settings.showWarnings = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Turret Analytics", settings.showTurretInfo, v -> {
            settings.showTurretInfo = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Drill Analytics", settings.showDrillInfo, v -> {
            settings.showDrillInfo = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Connection Info", settings.showConnectionInfo, v -> {
            settings.showConnectionInfo = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Storage Breakdown", settings.showStorageBreakdown, v -> {
            settings.showStorageBreakdown = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Production History", settings.showProductionHistory, v -> {
            settings.showProductionHistory = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Repair Indicators", settings.showRepairInfo, v -> {
            settings.showRepairInfo = v;
            settings.save();
        }).colspan(2).left().row();
        
        t.check("Team Stats (PvP)", settings.showTeamStats, v -> {
            settings.showTeamStats = v;
            settings.save();
        }).colspan(2).left().row();
    }
    
    void addPresetsAndInfo(Table t) {
        t.add("[accent]" + FormatUtil.repeat("═", 12) + " Presets " + FormatUtil.repeat("═", 12)).center().colspan(2).padTop(12f).padBottom(8f).row();
        
        Table presetRow1 = new Table();
        presetRow1.button("Minimal", Icon.zoom, () -> {
            settings.applyPreset("minimal");
            colors.applyTheme(settings.colorTheme);
            Vars.ui.showInfo("[lime]Applied Minimal preset");
        }).size(140f, 50f).pad(4f);
        
        presetRow1.button("Balanced", Icon.settings, () -> {
            settings.applyPreset("balanced");
            colors.applyTheme(settings.colorTheme);
            Vars.ui.showInfo("[lime]Applied Balanced preset");
        }).size(140f, 50f).pad(4f);
        
        t.add(presetRow1).colspan(2).center().padTop(8f).row();
        
        Table presetRow2 = new Table();
        presetRow2.button("Maximum", Icon.zoom, () -> {
            settings.applyPreset("maximum");
            colors.applyTheme(settings.colorTheme);
            Vars.ui.showInfo("[lime]Applied Maximum preset");
        }).size(140f, 50f).pad(4f);
        
        presetRow2.button("Combat", Icon.units, () -> {
            settings.applyPreset("combat");
            colors.applyTheme(settings.colorTheme);
            Vars.ui.showInfo("[lime]Applied Combat preset");
        }).size(140f, 50f).pad(4f);
        
        t.add(presetRow2).colspan(2).center().padTop(4f).row();
        
        t.add("[accent]" + FormatUtil.repeat("═", 15) + " Info " + FormatUtil.repeat("═", 15)).center().colspan(2).padTop(12f).padBottom(8f).row();
        
        t.add("[lightgray]Enhanced tooltips with vision cones,\nore detection, and improved visuals").colspan(2).center().padTop(8f).row();
        
        t.add("[sky]Version 5.0 - Vision & Resources").colspan(2).center().padTop(8f).row();
        
        t.button("Reset to Defaults", Icon.refresh, () -> {
            settings.resetToDefaults();
            colors.applyTheme(settings.colorTheme);
            Vars.ui.showInfo("[lime]Reset to defaults");
        }).size(200f, 50f).colspan(2).center().padTop(16f);
    }
}