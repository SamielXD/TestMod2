package tooltipsplus.config;

import arc.Core;

public class Settings {
    public boolean enabled = true;
    public boolean compactMode = false;
    public boolean showPowerDetails = true;
    public boolean showItemFlow = true;
    public boolean showUnitAdvanced = true;
    public boolean showWarnings = true;
    public boolean showTurretInfo = true;
    public boolean showConnectionInfo = true;
    public boolean showDrillInfo = true;
    public boolean showTeamStats = true;
    public boolean showRepairInfo = true;
    public boolean showStorageBreakdown = true;
    public boolean showProductionHistory = true;
    public boolean showHealthBars = true;
    public boolean showShieldStacks = true;
    public boolean showRangeIndicators = true;
    public boolean showEffectRanges = true;
    public boolean animateRanges = true;
    public boolean showTeamRanges = true;
    public boolean showUnitRanges = true;
    public boolean showVisionCones = true;
    public boolean showOreTooltips = true;
    public int tooltipOpacity = 8;
    public boolean followCursor = true;
    public boolean showIcons = true;
    public int fontSize = 1;
    public String colorTheme = "default";
    public boolean playHoverSound = false;
    public boolean highlightHovered = true;
    public int maxTooltipLines = 20;
    public float rangeOpacity = 0.25f;
    public float effectRangeOpacity = 0.15f;
    public float healthBarHeight = 6f;
    public float shieldBarHeight = 4f;
    public float hoverDelay = 0.15f;
    
    public void load() {
        enabled = Core.settings.getBool("ttp-enabled", true);
        showPowerDetails = Core.settings.getBool("ttp-power", true);
        showItemFlow = Core.settings.getBool("ttp-itemflow", true);
        showUnitAdvanced = Core.settings.getBool("ttp-unitadv", true);
        compactMode = Core.settings.getBool("ttp-compact", false);
        showWarnings = Core.settings.getBool("ttp-warnings", true);
        showTurretInfo = Core.settings.getBool("ttp-turret", true);
        showConnectionInfo = Core.settings.getBool("ttp-connections", true);
        showDrillInfo = Core.settings.getBool("ttp-drill", true);
        showTeamStats = Core.settings.getBool("ttp-team", true);
        showRepairInfo = Core.settings.getBool("ttp-repair", true);
        showStorageBreakdown = Core.settings.getBool("ttp-storage", true);
        showProductionHistory = Core.settings.getBool("ttp-history", true);
        showHealthBars = Core.settings.getBool("ttp-healthbars", true);
        showShieldStacks = Core.settings.getBool("ttp-shields", true);
        showRangeIndicators = Core.settings.getBool("ttp-ranges", true);
        showEffectRanges = Core.settings.getBool("ttp-effects", true);
        animateRanges = Core.settings.getBool("ttp-animate", true);
        showTeamRanges = Core.settings.getBool("ttp-teamrange", true);
        showUnitRanges = Core.settings.getBool("ttp-unitrange", true);
        showVisionCones = Core.settings.getBool("ttp-visioncones", true);
        showOreTooltips = Core.settings.getBool("ttp-oretooltips", true);
        tooltipOpacity = Core.settings.getInt("ttp-opacity", 8);
        followCursor = Core.settings.getBool("ttp-follow", true);
        showIcons = Core.settings.getBool("ttp-icons", true);
        fontSize = Core.settings.getInt("ttp-fontsize", 1);
        colorTheme = Core.settings.getString("ttp-theme", "default");
        playHoverSound = Core.settings.getBool("ttp-sound", false);
        highlightHovered = Core.settings.getBool("ttp-highlight", true);
        maxTooltipLines = Core.settings.getInt("ttp-maxlines", 20);
        rangeOpacity = Core.settings.getInt("ttp-rangeopacity", 25) / 100f;
        effectRangeOpacity = Core.settings.getInt("ttp-effectopacity", 15) / 100f;
    }
    
    public void save() {
        Core.settings.put("ttp-enabled", enabled);
        Core.settings.put("ttp-power", showPowerDetails);
        Core.settings.put("ttp-itemflow", showItemFlow);
        Core.settings.put("ttp-unitadv", showUnitAdvanced);
        Core.settings.put("ttp-compact", compactMode);
        Core.settings.put("ttp-warnings", showWarnings);
        Core.settings.put("ttp-turret", showTurretInfo);
        Core.settings.put("ttp-connections", showConnectionInfo);
        Core.settings.put("ttp-drill", showDrillInfo);
        Core.settings.put("ttp-team", showTeamStats);
        Core.settings.put("ttp-repair", showRepairInfo);
        Core.settings.put("ttp-storage", showStorageBreakdown);
        Core.settings.put("ttp-history", showProductionHistory);
        Core.settings.put("ttp-healthbars", showHealthBars);
        Core.settings.put("ttp-shields", showShieldStacks);
        Core.settings.put("ttp-ranges", showRangeIndicators);
        Core.settings.put("ttp-effects", showEffectRanges);
        Core.settings.put("ttp-animate", animateRanges);
        Core.settings.put("ttp-teamrange", showTeamRanges);
        Core.settings.put("ttp-unitrange", showUnitRanges);
        Core.settings.put("ttp-visioncones", showVisionCones);
        Core.settings.put("ttp-oretooltips", showOreTooltips);
        Core.settings.put("ttp-opacity", tooltipOpacity);
        Core.settings.put("ttp-follow", followCursor);
        Core.settings.put("ttp-icons", showIcons);
        Core.settings.put("ttp-fontsize", fontSize);
        Core.settings.put("ttp-theme", colorTheme);
        Core.settings.put("ttp-sound", playHoverSound);
        Core.settings.put("ttp-highlight", highlightHovered);
        Core.settings.put("ttp-maxlines", maxTooltipLines);
        Core.settings.put("ttp-rangeopacity", (int)(rangeOpacity * 100));
        Core.settings.put("ttp-effectopacity", (int)(effectRangeOpacity * 100));
        Core.settings.forceSave();
    }
    
    public void applyPreset(String preset) {
        switch(preset) {
            case "minimal":
                compactMode = true;
                showPowerDetails = false;
                showItemFlow = false;
                showRangeIndicators = false;
                showVisionCones = false;
                tooltipOpacity = 6;
                break;
            case "balanced":
                compactMode = false;
                showPowerDetails = true;
                showRangeIndicators = true;
                showVisionCones = true;
                tooltipOpacity = 8;
                break;
            case "maximum":
                compactMode = false;
                showPowerDetails = true;
                showProductionHistory = true;
                showRangeIndicators = true;
                showVisionCones = true;
                showOreTooltips = true;
                tooltipOpacity = 9;
                break;
            case "combat":
                compactMode = true;
                showTurretInfo = true;
                showRangeIndicators = true;
                showTeamRanges = true;
                showVisionCones = true;
                colorTheme = "neon";
                break;
        }
        save();
    }
    
    public void resetToDefaults() {
        enabled = true;
        compactMode = false;
        showPowerDetails = true;
        showItemFlow = true;
        showUnitAdvanced = true;
        showWarnings = true;
        showIcons = true;
        showTurretInfo = true;
        showConnectionInfo = true;
        showDrillInfo = true;
        showTeamStats = true;
        showRepairInfo = true;
        showStorageBreakdown = true;
        showProductionHistory = true;
        showHealthBars = true;
        showShieldStacks = true;
        showRangeIndicators = true;
        showEffectRanges = true;
        animateRanges = true;
        showUnitRanges = true;
        showTeamRanges = false;
        showVisionCones = true;
        showOreTooltips = true;
        tooltipOpacity = 8;
        followCursor = true;
        hoverDelay = 0.15f;
        fontSize = 1;
        colorTheme = "default";
        playHoverSound = false;
        highlightHovered = true;
        maxTooltipLines = 20;
        rangeOpacity = 0.25f;
        effectRangeOpacity = 0.15f;
        save();
    }
}