package tooltipsplus.ui;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.world.*;
import tooltipsplus.config.Settings;
import tooltipsplus.data.*;
import tooltipsplus.util.*;

public class TooltipRenderer {
    private Settings settings;
    private ColorUtil colors;
    private BuildingData buildingData;
    private UnitData unitData;
    
    private Table tooltipTable;
    private Building lastHoveredBuilding;
    private Unit lastHoveredUnit;
    private float hoverTimer = 0f;
    private Building pinnedBuilding = null;
    private boolean isPinned = false;
    
    private float[] productionHistory = new float[60];
    private int historyIndex = 0;
    private Interval updateInterval = new Interval(2);
    
    public TooltipRenderer(Settings settings, ColorUtil colors) {
        this.settings = settings;
        this.colors = colors;
        this.buildingData = new BuildingData(settings, colors);
        this.unitData = new UnitData(settings, colors);
        
        tooltipTable = new Table(Styles.black);
        tooltipTable.background(Tex.buttonEdge3);
        tooltipTable.margin(6f);
        tooltipTable.visible = false;
        Vars.ui.hudGroup.addChild(tooltipTable);
    }
    
    public void update() {
        if (!settings.enabled || Vars.state.isMenu()) {
            tooltipTable.visible = false;
            hoverTimer = 0f;
            return;
        }
        
        if (updateInterval.get(0, 60f)) {
            updateProductionTracking();
        }
        
        Vec2 mousePos = arc.Core.input.mouseWorld();
        Tile hoverTile = Vars.world.tileWorld(mousePos.x, mousePos.y);
        Building hoveredBuilding = (hoverTile != null) ? hoverTile.build : null;
        
        Unit hoveredUnit = Groups.unit.find(u -> {
            return u.within(mousePos.x, mousePos.y, u.hitSize / 2f);
        });
        
        if (isPinned && pinnedBuilding != null) {
            showBuildingTooltip(pinnedBuilding);
            return;
        }
        
        if (hoveredBuilding != lastHoveredBuilding || hoveredUnit != lastHoveredUnit) {
            hoverTimer = 0f;
            lastHoveredBuilding = hoveredBuilding;
            lastHoveredUnit = hoveredUnit;
            
            if (settings.playHoverSound && (hoveredBuilding != null || hoveredUnit != null)) {
                Sounds.click.play(0.3f);
            }
        }
        
        if (hoveredBuilding != null || hoveredUnit != null) {
            hoverTimer += Time.delta / 60f;
        }
        
        if (settings.highlightHovered && hoveredBuilding != null) {
            Lines.stroke(2f);
            Draw.color(Color.cyan, 0.6f);
            Lines.rect(hoveredBuilding.x - hoveredBuilding.block.size * 4f, 
                      hoveredBuilding.y - hoveredBuilding.block.size * 4f,
                      hoveredBuilding.block.size * 8f,
                      hoveredBuilding.block.size * 8f);
            Draw.reset();
        }
        
        if (hoverTimer >= settings.hoverDelay) {
            if (hoveredBuilding != null) {
                showBuildingTooltip(hoveredBuilding);
                return;
            } else if (hoveredUnit != null) {
                showUnitTooltip(hoveredUnit);
                return;
            }
        }
        
        tooltipTable.visible = false;
    }
    
    void showBuildingTooltip(Building build) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        Table titleRow = new Table();
        if (settings.showIcons && build.block.fullIcon != null) {
            titleRow.image(build.block.fullIcon).size(24f * (settings.fontSize + 1)).padRight(4f);
        }
        titleRow.add(colors.accentColor + build.block.localizedName).style(Styles.outlineLabel);
        tooltipTable.add(titleRow).left().row();
        
        if (!settings.compactMode) {
            tooltipTable.add(colors.infoColor + FormatUtil.repeat("─", 20)).padTop(2f).padBottom(2f).row();
        }
        
        float healthPercent = (build.health / build.maxHealth) * 100f;
        String healthColor = colors.getPercentColor(healthPercent);
        
        tooltipTable.add("🛡 " + colors.statColor + "HP: " + healthColor + (int)build.health + colors.infoColor + "/" + (int)build.maxHealth).left().row();
        
        if (settings.showWarnings && healthPercent < 30f) {
            tooltipTable.add("  " + colors.warningColor + "⚠ Critical Damage!").left().row();
        }
        
        if (build.power != null && settings.showPowerDetails) {
            buildingData.addPowerInfo(tooltipTable, build);
        }
        
        if (build.items != null && settings.showStorageBreakdown) {
            buildingData.addItemInfo(tooltipTable, build);
        }
        
        if (build.liquids != null) {
            buildingData.addLiquidInfo(tooltipTable, build);
        }
        
        if (settings.showItemFlow) {
            buildingData.addProductionInfo(tooltipTable, build);
        }
        
        if (settings.showTurretInfo) {
            buildingData.addTurretInfo(tooltipTable, build);
        }
        
        if (settings.showDrillInfo) {
            buildingData.addDrillInfo(tooltipTable, build);
        }
        
        if (settings.showConnectionInfo) {
            buildingData.addConnectionInfo(tooltipTable, build);
        }
        
        buildingData.addConveyorFlow(tooltipTable, build);
        
        if (settings.showPowerDetails && build.power != null) {
            buildingData.addPowerNetworkInfo(tooltipTable, build);
        }
        
        if (settings.showTeamStats) {
            buildingData.addTeamInfo(tooltipTable, build);
        }
        
        if (isPinned) {
            tooltipTable.add("[royal]📌 PINNED (P to unpin)").left().padTop(4f).row();
        }
        
        positionTooltip();
        tooltipTable.pack();
    }
    
    void showUnitTooltip(Unit unit) {
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        Table titleRow = new Table();
        if (settings.showIcons && unit.type.fullIcon != null) {
            titleRow.image(unit.type.fullIcon).size(24f * (settings.fontSize + 1)).padRight(4f);
        }
        titleRow.add(colors.accentColor + unit.type.localizedName).style(Styles.outlineLabel);
        tooltipTable.add(titleRow).left().row();
        
        if (!settings.compactMode) {
            tooltipTable.add(colors.infoColor + FormatUtil.repeat("─", 20)).padTop(2f).padBottom(2f).row();
        }
        
        unitData.addHealthInfo(tooltipTable, unit);
        unitData.addAdvancedInfo(tooltipTable, unit);
        unitData.addWeaponInfo(tooltipTable, unit);
        
        positionTooltip();
        tooltipTable.pack();
    }
    
    void positionTooltip() {
        Vec2 screenPos = arc.Core.input.mouse();
        float x = screenPos.x + 20f;
        float y = screenPos.y + 20f;
        
        if (x + tooltipTable.getWidth() > arc.Core.graphics.getWidth()) {
            x = screenPos.x - tooltipTable.getWidth() - 10f;
        }
        if (y + tooltipTable.getHeight() > arc.Core.graphics.getHeight()) {
            y = screenPos.y - tooltipTable.getHeight() - 10f;
        }
        
        if (settings.followCursor) {
            tooltipTable.setPosition(x, y);
        } else {
            tooltipTable.setPosition(
                arc.Core.graphics.getWidth() - tooltipTable.getWidth() - 20f,
                20f
            );
        }
        
        tooltipTable.color.a = settings.tooltipOpacity / 10f;
    }
    
    void updateProductionTracking() {
        if (lastHoveredBuilding != null) {
            float status = lastHoveredBuilding.power != null ? lastHoveredBuilding.power.status : 0f;
            productionHistory[historyIndex] = status;
            historyIndex = (historyIndex + 1) % 60;
        }
    }
    
    public void setPinned(Building building) {
        if (pinnedBuilding == building) {
            pinnedBuilding = null;
            isPinned = false;
        } else {
            pinnedBuilding = building;
            isPinned = true;
        }
    }
    
    public Building getLastHoveredBuilding() {
        return lastHoveredBuilding;
    }
}