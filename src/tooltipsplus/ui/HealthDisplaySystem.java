package tooltipsplus.ui;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import tooltipsplus.config.Settings;

public class HealthDisplaySystem {
    private Settings settings;
    
    private static final float BAR_WIDTH = 60f;
    private static final float BAR_HEIGHT = 8f;
    private static final float SHIELD_HEIGHT = 6f;
    private static final float TEXT_OFFSET_Y = 4f;
    private static final float BAR_OFFSET_Y = 20f;
    
    public HealthDisplaySystem(Settings settings) {
        this.settings = settings;
    }
    
    public void draw() {
        if (!settings.showHealthBars) return;
        
        Groups.unit.each(unit -> {
            if (!unit.isValid() || unit.dead || unit.health <= 0) return;
            drawUnitHealth(unit);
        });
        
        Groups.build.each(build -> {
            if (!build.isValid() || build.dead || build.health <= 0) return;
            drawBuildingHealth(build);
        });
    }
    
    void drawUnitHealth(Unit unit) {
        float x = unit.x;
        float y = unit.y + unit.hitSize / 2f + BAR_OFFSET_Y;
        float width = Math.max(BAR_WIDTH, unit.hitSize * 1.2f);
        
        drawHealthBar(x, y, unit.health, unit.maxHealth, unit.shield, width, unit.team.color);
    }
    
    void drawBuildingHealth(Building build) {
        float x = build.x;
        float y = build.y + build.block.size * 4f + BAR_OFFSET_Y;
        float width = Math.max(BAR_WIDTH, build.block.size * 8f * 0.8f);
        
        drawHealthBar(x, y, build.health, build.maxHealth, 0f, width, build.team.color);
    }
    
    void drawHealthBar(float x, float y, float health, float maxHealth, float shield, float width, Color teamColor) {
        float healthPercent = Mathf.clamp(health / maxHealth);
        int shieldStacks = shield > 0 ? Mathf.ceil(shield / maxHealth) : 0;
        float shieldPercent = shieldStacks > 0 ? (shield % maxHealth) / maxHealth : 0f;
        if (shieldPercent < 0.01f && shieldStacks > 0) shieldPercent = 1f;
        
        Draw.color(Color.black, 0.7f);
        Fill.rect(x, y, width + 4f, BAR_HEIGHT + 4f);
        
        Draw.color(Color.darkGray);
        Fill.rect(x, y, width, BAR_HEIGHT);
        
        Draw.color(teamColor);
        Fill.rect(x - width/2f + (width * healthPercent)/2f, y, width * healthPercent, BAR_HEIGHT);
        
        if (shieldStacks > 0) {
            float shieldY = y + BAR_HEIGHT/2f + SHIELD_HEIGHT/2f + 2f;
            
            Color shieldColor = getShieldColor(shieldStacks, teamColor);
            float pulse = Mathf.absin(Time.time / 60f * 8f, 0.3f);
            
            Draw.color(Color.black, 0.7f);
            Fill.rect(x, shieldY, width + 4f, SHIELD_HEIGHT + 4f);
            
            Draw.color(shieldColor, 0.3f);
            Fill.rect(x, shieldY, width, SHIELD_HEIGHT);
            
            Draw.color(shieldColor, 0.7f + pulse);
            Fill.rect(x - width/2f + (width * shieldPercent)/2f, shieldY, width * shieldPercent, SHIELD_HEIGHT);
            
            if (shieldStacks > 1) {
                String stackText = "x" + shieldStacks;
                Color textColor = getShieldStackTextColor(shieldStacks);
                
                Fonts.outline.getData().setScale(0.7f);
                Fonts.outline.setColor(textColor);
                Fonts.outline.draw(stackText, x + width/2f + 4f, shieldY + SHIELD_HEIGHT/2f + TEXT_OFFSET_Y);
                Fonts.outline.getData().setScale(1f);
            }
        }
        
        String healthText = (int)health + "/" + (int)maxHealth;
        Fonts.outline.getData().setScale(0.65f);
        Fonts.outline.setColor(Color.white);
        
        GlyphLayout layout = new GlyphLayout(Fonts.outline, healthText);
        Fonts.outline.draw(healthText, x - layout.width/2f, y + TEXT_OFFSET_Y);
        Fonts.outline.getData().setScale(1f);
        
        Draw.reset();
    }
    
    Color getShieldColor(int stacks, Color teamColor) {
        int cycle = (stacks - 1) % 3;
        switch(cycle) {
            case 0: return teamColor.diff(Pal.heal) < 0.1f ? Pal.lancerLaser : Pal.heal;
            case 1: return teamColor.diff(Pal.accent) < 0.1f ? Color.sky : Pal.accent;
            case 2: return teamColor.diff(Pal.reactorPurple) < 0.1f ? Pal.heal : Pal.reactorPurple;
            default: return Pal.accent;
        }
    }
    
    Color getShieldStackTextColor(int stacks) {
        if (stacks >= 10000) return Color.crimson;
        if (stacks >= 1000) return Color.red;
        if (stacks >= 100) return Pal.accent;
        if (stacks >= 10) return Color.white;
        return Color.lightGray;
    }
}