package tooltipsplus.data;

import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.Tile;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.defense.turrets.*;
import tooltipsplus.config.Settings;
import tooltipsplus.util.ColorUtil;
import tooltipsplus.util.FormatUtil;

public class BuildingData {
    private Settings settings;
    private ColorUtil colors;
    
    public BuildingData(Settings settings, ColorUtil colors) {
        this.settings = settings;
        this.colors = colors;
    }
    
    public void addPowerInfo(Table table, Building build) {
        if (build.power == null) return;
        
        table.add(colors.statColor + "─ Power ─").padTop(4f).row();
        
        if (build.block.consPower != null && build.block.consPower.capacity > 0) {
            float stored = build.power.status * build.block.consPower.capacity;
            float capacity = build.block.consPower.capacity;
            table.add("⚡" + colors.statColor + "Battery: " + colors.accentColor + (int)stored + colors.infoColor + "/" + (int)capacity).left().row();
        }
        
        if (build.block instanceof PowerGenerator) {
            PowerGenerator gen = (PowerGenerator)build.block;
            float production = gen.powerProduction * 60f;
            table.add("  " + colors.successColor + "+ " + FormatUtil.formatNumber(production) + "/s").left().row();
        }
        
        if (build.block.consPower != null && build.block.consPower.usage > 0) {
            float usage = build.block.consPower.usage * 60f;
            table.add("  " + colors.warningColor + "- " + FormatUtil.formatNumber(usage) + "/s").left().row();
        }
    }
    
    public void addItemInfo(Table table, Building build) {
        if (build.items == null || build.items.total() == 0) return;
        
        table.add(colors.statColor + "─ Items ─").padTop(4f).row();
        
        int total = build.items.total();
        int capacity = build.block.itemCapacity;
        float fillPercent = (total / (float)capacity) * 100f;
        
        table.add("📦 " + colors.statColor + "Storage: " + colors.getPercentColor(fillPercent) + total + colors.infoColor + "/" + capacity).left().row();
        
        if (settings.showWarnings && fillPercent > 90f) {
            table.add("  " + colors.warningColor + "⚠ Nearly Full!").left().row();
        }
        
        int itemCount = 0;
        for (int i = 0; i < Vars.content.items().size && itemCount < 3; i++) {
            var item = Vars.content.item(i);
            int amount = build.items.get(item);
            if (amount > 0) {
                table.add("  " + item.emoji() + " " + colors.infoColor + item.localizedName + ": " + colors.accentColor + amount).left().row();
                itemCount++;
            }
        }
    }
    
    public void addLiquidInfo(Table table, Building build) {
        if (build.liquids == null || build.liquids.currentAmount() < 0.01f) return;
        
        table.add(colors.statColor + "─ Liquids ─").padTop(4f).row();
        
        float total = build.liquids.currentAmount();
        float capacity = build.block.liquidCapacity;
        float fillPercent = (total / capacity) * 100f;
        
        table.add("💧" + colors.statColor + "Tank: " + colors.getPercentColor(fillPercent) + Strings.autoFixed(total, 1) + colors.infoColor + "/" + Strings.autoFixed(capacity, 1)).left().row();
    }
    
    public void addProductionInfo(Table table, Building build) {
        if (!(build.block instanceof GenericCrafter)) return;
        
        GenericCrafter crafter = (GenericCrafter)build.block;
        
        if (crafter.outputItems != null && crafter.outputItems.length > 0) {
            table.add(colors.statColor + "─ Production ─").padTop(4f).row();
            
            for (var output : crafter.outputItems) {
                float rate = (output.amount / crafter.craftTime) * 60f;
                table.add("  → " + output.item.emoji() + " " + colors.infoColor + Strings.autoFixed(rate, 1) + "/s").left().row();
            }
            
            float efficiency = build.efficiency;
            if (efficiency < 1f) {
                table.add("  " + colors.warningColor + "⚠ " + (int)(efficiency * 100f) + "% speed").left().row();
            }
        }
    }
    
    public void addTurretInfo(Table table, Building build) {
        if (!(build.block instanceof Turret)) return;
        
        Turret turret = (Turret)build.block;
        
        table.add(colors.statColor + "─ Turret ─").padTop(4f).row();
        table.add("  " + colors.infoColor + "Range: " + colors.accentColor + (int)(turret.range / 8f) + " tiles").left().row();
        
        if (turret.reload > 0) {
            float shotsPerMin = (60f / turret.reload) * 60f;
            table.add("  " + colors.infoColor + "Rate: " + colors.accentColor + Strings.autoFixed(shotsPerMin, 1) + "/min").left().row();
        }
        
        if (build instanceof Turret.TurretBuild) {
            Turret.TurretBuild tb = (Turret.TurretBuild)build;
            if (tb.hasAmmo()) {
                table.add("  " + colors.successColor + "✓ Ammo Ready").left().row();
            } else {
                table.add("  " + colors.warningColor + "✗ No Ammo").left().row();
            }
        }
    }
    
    public void addDrillInfo(Table table, Building build) {
        if (!(build.block instanceof Drill)) return;
        
        Drill drill = (Drill)build.block;
        
        table.add(colors.statColor + "─ Drill ─").padTop(4f).row();
        table.add("  " + colors.infoColor + "Tier: " + colors.accentColor + drill.tier).left().row();
        
        if (drill.drillTime > 0) {
            float rate = 60f / drill.drillTime;
            table.add("  " + colors.infoColor + "Speed: " + colors.accentColor + Strings.autoFixed(rate, 1) + "/s").left().row();
        }
        
        Tile tile = build.tile;
        if (tile != null && tile.drop() != null) {
            table.add("  ⛏ " + tile.drop().emoji() + " " + colors.infoColor + tile.drop().localizedName).left().row();
        }
    }
    
    public void addConnectionInfo(Table table, Building build) {
        int connections = build.proximity.size;
        
        if (connections > 0) {
            table.add(colors.statColor + "─ Connections ─").padTop(4f).row();
            table.add("  " + colors.infoColor + "Links: " + colors.accentColor + connections).left().row();
        }
    }
    
    public void addConveyorFlow(Table table, Building build) {
        if (build.block instanceof Conveyor) {
            Conveyor conveyor = (Conveyor)build.block;
            float itemsPerSec = conveyor.speed * 60f;
            table.add("→" + colors.statColor + "Flow: " + colors.accentColor + Strings.autoFixed(itemsPerSec, 1) + " items/s").left().row();
        }
    }
    
    public void addPowerNetworkInfo(Table table, Building build) {
        if (build.power != null && build.power.graph != null) {
            var graph = build.power.graph;
            float production = graph.getPowerProduced() * 60f;
            float consumption = graph.getPowerNeeded() * 60f;
            float balance = production - consumption;
            
            if (production > 0 || consumption > 0) {
                table.add(colors.infoColor + "─ Grid ─").padTop(4f).row();
                
                String balanceColor = balance > 0 ? colors.successColor : colors.warningColor;
                String balanceSymbol = balance > 0 ? "+" : "";
                table.add(colors.statColor + "Balance: " + balanceColor + balanceSymbol + Strings.autoFixed(balance, 1) + "/s").left().row();
            }
        }
    }
    
    public void addTeamInfo(Table table, Building build) {
        if (!Vars.state.rules.pvp) return;
        
        table.add(colors.statColor + "─ Team ─").padTop(4f).row();
        table.add("  " + build.team.emoji + " " + build.team.coloredName()).left().row();
        
        if (build.team != Vars.player.team()) {
            table.add("  " + colors.warningColor + "🛡 Enemy Structure").left().row();
        }
    }
}