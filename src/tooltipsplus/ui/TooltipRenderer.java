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
import mindustry.content.Blocks;
import mindustry.type.Item;
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
    private Tile lastHoveredTile;
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
        tooltipTable.toFront();
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
        
        if (hoveredBuilding != lastHoveredBuilding || hoveredUnit != lastHoveredUnit || hoverTile != lastHoveredTile) {
            hoverTimer = 0f;
            lastHoveredBuilding = hoveredBuilding;
            lastHoveredUnit = hoveredUnit;
            lastHoveredTile = hoverTile;
            
            if (settings.playHoverSound && (hoveredBuilding != null || hoveredUnit != null || (hoverTile != null && isOreOrResource(hoverTile)))) {
                Sounds.click.play(0.3f);
            }
        }
        
        if (hoveredBuilding != null || hoveredUnit != null || (hoverTile != null && isOreOrResource(hoverTile))) {
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
            } else if (hoverTile != null && isOreOrResource(hoverTile)) {
                showOreTooltip(hoverTile);
                return;
            }
        }
        
        tooltipTable.visible = false;
    }
    
    boolean isOreOrResource(Tile tile) {
        if (tile == null) return false;
        if (tile.overlay() != Blocks.air && tile.overlay().itemDrop != null) return true;
        if (tile.floor() != Blocks.air && tile.floor().liquidDrop != null) return true;
        if (tile.block() != Blocks.air) return true;
        return false;
    }
    
    void showOreTooltip(Tile tile) {
        if (tile == null) return;
        
        tooltipTable.clear();
        tooltipTable.visible = true;
        
        Item drop = null;
        mindustry.type.Liquid liquidDrop = null;
        
        if (tile.overlay() != null && tile.overlay().itemDrop != null) {
            drop = tile.overlay().itemDrop;
        } else if (tile.floor() != null && tile.floor().liquidDrop != null) {
            liquidDrop = tile.floor().liquidDrop;
        }
        
        if (drop != null) {
            Table titleRow = new Table();
            if (settings.showIcons && drop.fullIcon != null) {
                titleRow.image(drop.fullIcon).size(24f * (settings.fontSize + 1)).padRight(4f);
            }
            titleRow.add(colors.accentColor + drop.localizedName).style(Styles.outlineLabel);
            tooltipTable.add(titleRow).left().row();
            
            if (!settings.compactMode) {
                tooltipTable.add(colors.infoColor + FormatUtil.repeat("─", 20)).padTop(2f).padBottom(2f).row();
            }
            
            tooltipTable.add(colors.statColor + "Type: " + colors.infoColor + "Ore Resource").left().row();
            
            if (tile.overlay().name != null) {
                tooltipTable.add(colors.statColor + "Block: " + colors.infoColor + tile.overlay().localizedName).left().row();
            }
            
            if (drop.hardness > 0) {
                tooltipTable.add(colors.statColor + "Hardness: " + colors.infoColor + (int)drop.hardness).left().row();
            }
            
            if (drop.cost > 0) {
                tooltipTable.add(colors.statColor + "Value: " + colors.infoColor + drop.cost).left().row();
            }
            
            if (drop.explosiveness > 0) {
                tooltipTable.add(colors.warningColor + "⚠ Explosive: " + (int)(drop.explosiveness * 100) + "%").left().row();
            }
            
            if (drop.flammability > 0) {
                tooltipTable.add(colors.warningColor + "🔥 Flammable: " + (int)(drop.flammability * 100) + "%").left().row();
            }
            
            if (drop.radioactivity > 0) {
                tooltipTable.add(colors.warningColor + "☢ Radioactive: " + (int)(drop.radioactivity * 100) + "%").left().row();
            }
        } else if (liquidDrop != null) {
            Table titleRow = new Table();
            if (settings.showIcons && liquidDrop.fullIcon != null) {
                titleRow.image(liquidDrop.fullIcon).size(24f * (settings.fontSize + 1)).padRight(4f);
            }
            titleRow.add(colors.accentColor + liquidDrop.localizedName).style(Styles.outlineLabel);
            tooltipTable.add(titleRow).left().row();
            
            if (!settings.compactMode) {
                tooltipTable.add(colors.infoColor + FormatUtil.repeat("─", 20)).padTop(2f).padBottom(2f).row();
            }
            
            tooltipTable.add(colors.statColor + "Type: " + colors.infoColor + "Liquid Pool").left().row();
            
            if (tile.floor().name != null) {
                tooltipTable.add(colors.statColor + "Block: " + colors.infoColor + tile.floor().localizedName).left().row();
            }
            
            tooltipTable.add(colors.statColor + "Temperature: " + colors.infoColor + (int)(liquidDrop.temperature * 100) + "°C").left().row();
            
            if (liquidDrop.viscosity > 0) {
                tooltipTable.add(colors.statColor + "Viscosity: " + colors.infoColor + String.format("%.2f", liquidDrop.viscosity)).left().row();
            }
            
            if (liquidDrop.flammability > 0) {
                tooltipTable.add(colors.warningColor + "🔥 Flammable: " + (int)(liquidDrop.flammability * 100) + "%").left().row();
            }
            
            if (liquidDrop.explosiveness > 0) {
                tooltipTable.add(colors.warningColor + "⚠ Explosive: " + (int)(liquidDrop.explosiveness * 100) + "%").left().row();
            }
        } else if (tile.block() != Blocks.air) {
            Table titleRow = new Table();
            if (settings.showIcons && tile.block().fullIcon != null) {
                titleRow.image(tile.block().fullIcon).size(24f * (settings.fontSize + 1)).padRight(4f);
            }
            titleRow.add(colors.accentColor + tile.block().localizedName).style(Styles.outlineLabel);
            tooltipTable.add(titleRow).left().row();
            
            if (!settings.compactMode) {
                tooltipTable.add(colors.infoColor + FormatUtil.repeat("─", 20)).padTop(2f).padBottom(2f).row();
            }
            
            tooltipTable.add(colors.statColor + "Type: " + colors.infoColor + "Environment Block").left().row();
        }
        
        tooltipTable.add(colors.infoColor + "Position: " + tile.x + ", " + tile.y).left().padTop(4f).row();
        
        positionTooltip();
        tooltipTable.pack();
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
        
        if (build.block instanceof mindustry.world.blocks.units.Reconstructor) {
            addReconstructorInfo(tooltipTable, build);
        }
        
        if (build.block instanceof mindustry.world.blocks.units.UnitFactory) {
            addUnitFactoryInfo(tooltipTable, build);
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
    
    void addReconstructorInfo(Table table, Building build) {
        if (!(build.block instanceof mindustry.world.blocks.units.Reconstructor)) return;
        
        mindustry.world.blocks.units.Reconstructor reconstructor = (mindustry.world.blocks.units.Reconstructor)build.block;
        
        table.add(colors.accentColor + "⚙ Reconstructor Info:").left().row();
        
        if (reconstructor.upgrades.size > 0) {
            for (var upgrade : reconstructor.upgrades) {
                if (upgrade.length >= 2) {
                    table.add(colors.statColor + "  " + upgrade[0].localizedName + " → " + upgrade[1].localizedName).left().row();
                }
            }
        }
        
        if (build instanceof mindustry.world.blocks.units.Reconstructor.ReconstructorBuild) {
            mindustry.world.blocks.units.Reconstructor.ReconstructorBuild rb = (mindustry.world.blocks.units.Reconstructor.ReconstructorBuild)build;
            if (rb.unit() != null) {
                table.add(colors.infoColor + "  Current: " + rb.unit().type.localizedName).left().row();
                float progress = rb.progress() * 100f;
                table.add(colors.infoColor + "  Progress: " + (int)progress + "%").left().row();
            }
        }
    }
    
    void addUnitFactoryInfo(Table table, Building build) {
        if (!(build.block instanceof mindustry.world.blocks.units.UnitFactory)) return;
        
        mindustry.world.blocks.units.UnitFactory factory = (mindustry.world.blocks.units.UnitFactory)build.block;
        
        table.add(colors.accentColor + "🏭 Factory Info:").left().row();
        
        if (factory.plans.size > 0) {
            table.add(colors.statColor + "  Can produce:").left().row();
            for (var plan : factory.plans) {
                table.add(colors.infoColor + "    • " + plan.unit.localizedName).left().row();
            }
        }
        
        if (build instanceof mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild) {
            mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild fb = (mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild)build;
            if (fb.currentPlan >= 0 && fb.currentPlan < factory.plans.size) {
                var plan = factory.plans.get(fb.currentPlan);
                table.add(colors.infoColor + "  Building: " + plan.unit.localizedName).left().row();
                float progress = fb.progress * 100f;
                table.add(colors.infoColor + "  Progress: " + (int)progress + "%").left().row();
            }
        }
    }
}