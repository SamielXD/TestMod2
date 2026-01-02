package tooltipsplus.data;

import arc.scene.ui.layout.Table;
import arc.util.Strings;
import mindustry.gen.Unit;
import tooltipsplus.config.Settings;
import tooltipsplus.util.ColorUtil;

public class UnitData {
    private Settings settings;
    private ColorUtil colors;
    
    public UnitData(Settings settings, ColorUtil colors) {
        this.settings = settings;
        this.colors = colors;
    }
    
    public void addHealthInfo(Table table, Unit unit) {
        float healthPercent = (unit.health / unit.maxHealth) * 100f;
        String healthColor = colors.getPercentColor(healthPercent);
        
        table.add("🛡" + colors.statColor + "HP: " + healthColor + (int)unit.health + colors.infoColor + "/" + (int)unit.maxHealth).left().row();
        
        if (settings.showShieldStacks && unit.shield > 0) {
            table.add("🛡" + colors.statColor + "Shield: " + colors.successColor + (int)unit.shield).left().row();
        }
        
        if (unit.type.armor > 0) {
            table.add("  " + colors.infoColor + "Armor: " + colors.accentColor + (int)unit.type.armor).left().row();
        }
        
        if (settings.showWarnings && healthPercent < 25f) {
            table.add("  " + colors.warningColor + "⚠ Critical HP!").left().row();
        }
    }
    
    public void addAdvancedInfo(Table table, Unit unit) {
        if (!settings.showUnitAdvanced) return;
        
        table.add("✈" + colors.statColor + "Speed: " + colors.accentColor + Strings.autoFixed(unit.type.speed * 60f, 1)).left().row();
        
        if (unit.type.mineSpeed > 0) {
            table.add("⛏" + colors.statColor + "Mine: " + colors.accentColor + Strings.autoFixed(unit.type.mineSpeed, 1) + "/s").left().row();
        }
        
        if (unit.type.buildSpeed > 0) {
            table.add("🔨" + colors.statColor + "Build: " + colors.accentColor + Strings.autoFixed(unit.type.buildSpeed, 1) + "/s").left().row();
        }
        
        if (unit.type.itemCapacity > 0) {
            int carrying = unit.stack != null && unit.stack.item != null ? unit.stack.amount : 0;
            table.add("📦" + colors.statColor + "Carry: " + colors.accentColor + carrying + colors.infoColor + "/" + unit.type.itemCapacity).left().row();
        }
    }
    
    public void addWeaponInfo(Table table, Unit unit) {
        if (!settings.showUnitAdvanced || unit.type.weapons.size == 0) return;
        
        float totalDPS = 0f;
        for (var weapon : unit.type.weapons) {
            if (weapon.bullet != null) {
                float dps = weapon.bullet.damage * (60f / weapon.reload);
                totalDPS += dps;
            }
        }
        
        if (totalDPS > 0) {
            table.add("⚔" + colors.statColor + "DPS: " + colors.accentColor + Strings.autoFixed(totalDPS, 1)).left().row();
        }
    }
}
