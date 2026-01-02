package tooltipsplus.ui;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import tooltipsplus.config.Settings;

public class HealthDisplaySystem {
    private Settings settings;
    private Seq<DamageNumber> damageNumbers = new Seq<>();
    
    public HealthDisplaySystem(Settings settings) {
        this.settings = settings;
        setupHealthTracking();
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
        
        updateDamageNumbers();
    }
    
    void setupHealthTracking() {
        Groups.unit.each(unit -> {
            if (unit.health > 0) {
                trackEntity(unit);
            }
        });
        
        Groups.build.each(build -> {
            if (build.health > 0) {
                trackEntity(build);
            }
        });
    }
    
    void trackEntity(Healthc entity) {
        float lastHealth = entity.health();
        
        arc.Events.run(arc.util.Time.class, () -> {
            if (!entity.isValid() || entity.dead()) return;
            
            float currentHealth = entity.health();
            float diff = currentHealth - lastHealth;
            
            if (Math.abs(diff) > 0.1f) {
                float x = 0, y = 0;
                if (entity instanceof Posc) {
                    Posc pos = (Posc)entity;
                    x = pos.x();
                    y = pos.y();
                    if (entity instanceof Hitboxc) {
                        y += ((Hitboxc)entity).hitSize() / 2f + 20f;
                    }
                }
                
                spawnDamageNumber(x, y, diff);
            }
            
            lastHealth = currentHealth;
        });
    }
    
    void spawnDamageNumber(float x, float y, float amount) {
        damageNumbers.add(new DamageNumber(x, y, amount));
    }
    
    void updateDamageNumbers() {
        damageNumbers.each(dn -> {
            dn.update();
            dn.draw();
        });
        damageNumbers.removeAll(dn -> dn.lifetime <= 0);
    }
    
    void drawUnitHealth(Unit unit) {
        float x = unit.x;
        float y = unit.y + unit.hitSize / 2f + 25f;
        float width = Math.max(70f, unit.hitSize * 1.5f) * settings.healthBarScale;
        
        Color barColor = getTeamColor(unit.team);
        drawHealthBar(x, y, unit.health, unit.maxHealth, unit.shield, width, barColor);
    }
    
    void drawBuildingHealth(Building build) {
        float x = build.x;
        float y = build.y + build.block.size * 4f + 25f;
        float width = Math.max(70f, build.block.size * 10f) * settings.healthBarScale;
        
        Color barColor = getTeamColor(build.team);
        drawHealthBar(x, y, build.health, build.maxHealth, 0f, width, barColor);
    }
    
    Color getTeamColor(Team team) {
        if (team == Vars.player.team()) {
            return Color.valueOf("84f491");
        } else if (team.id == Vars.player.team().id) {
            return Color.sky;
        } else {
            return Color.valueOf("ff6b6b");
        }
    }
    
    void drawHealthBar(float x, float y, float health, float maxHealth, float shield, float width, Color teamColor) {
        float healthPercent = Mathf.clamp(health / maxHealth);
        int shieldStacks = shield > 0 ? Mathf.ceil(shield / maxHealth) : 0;
        float shieldPercent = shieldStacks > 0 ? (shield % maxHealth) / maxHealth : 0f;
        if (shieldPercent < 0.01f && shieldStacks > 0) shieldPercent = 1f;
        
        float barHeight = 10f * settings.healthBarScale;
        float shieldHeight = 7f * settings.healthBarScale;
        
        drawGradientBar(x, y, width, barHeight, healthPercent, teamColor, true);
        
        if (shieldStacks > 0) {
            float shieldY = y + barHeight/2f + shieldHeight/2f + 3f;
            Color shieldColor = getShieldColor(shieldStacks, teamColor);
            
            drawGradientBar(x, shieldY, width, shieldHeight, shieldPercent, shieldColor, false);
            
            if (shieldStacks > 1) {
                String stackText = "x" + shieldStacks;
                Color textColor = getShieldStackTextColor(shieldStacks);
                
                Fonts.outline.getData().setScale(0.8f * settings.healthBarScale);
                Fonts.outline.setColor(textColor);
                Fonts.outline.draw(stackText, x + width/2f + 6f, shieldY + shieldHeight/2f + 4f);
                Fonts.outline.getData().setScale(1f);
            }
            
            Fonts.outline.getData().setScale(0.7f * settings.healthBarScale);
            Fonts.outline.setColor(Color.white);
            GlyphLayout shieldIcon = new GlyphLayout(Fonts.outline, "\uE84D");
            Fonts.outline.draw("\uE84D", x - width/2f - shieldIcon.width - 2f, shieldY + shieldHeight/2f + 4f);
            Fonts.outline.getData().setScale(1f);
        }
        
        String healthText = "\uE813 " + (int)health + "/" + (int)maxHealth;
        Fonts.outline.getData().setScale(0.7f * settings.healthBarScale);
        Fonts.outline.setColor(Color.white);
        
        GlyphLayout layout = new GlyphLayout(Fonts.outline, healthText);
        Fonts.outline.draw(healthText, x - layout.width/2f, y + barHeight/2f + 5f);
        Fonts.outline.getData().setScale(1f);
        
        Draw.reset();
    }
    
    void drawGradientBar(float x, float y, float width, float height, float percent, Color baseColor, boolean isHealth) {
        Draw.color(Color.black, 0.8f);
        Fill.rect(x, y, width + 4f, height + 4f);
        
        Draw.color(Color.darkGray, 0.6f);
        Fill.rect(x, y, width, height);
        
        float fillWidth = width * percent;
        
        for (int i = 0; i < 5; i++) {
            float layer = i / 5f;
            float alpha = 0.3f + (0.7f * (1f - layer));
            float yOffset = (height / 2f) * layer;
            
            Draw.color(baseColor, alpha);
            Fill.rect(x - width/2f + fillWidth/2f, y - height/2f + yOffset, fillWidth, height - yOffset * 2f);
        }
        
        Draw.color(baseColor.cpy().lerp(Color.white, 0.4f), 0.9f);
        Lines.stroke(2f);
        Lines.rect(x - width/2f, y - height/2f, fillWidth, height);
        
        float pulse = Mathf.absin(Time.time / 60f * 3f, 0.2f);
        Draw.color(Color.white, 0.3f + pulse);
        Fill.rect(x - width/2f + fillWidth/2f, y + height/2f - 2f, fillWidth, 2f);
        
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
    
    class DamageNumber {
        float x, y;
        float amount;
        float lifetime = 90f;
        float initialY;
        
        DamageNumber(float x, float y, float amount) {
            this.x = x + Mathf.range(10f);
            this.y = y;
            this.initialY = y;
            this.amount = amount;
        }
        
        void update() {
            lifetime -= Time.delta;
            y += Time.delta * 0.8f;
        }
        
        void draw() {
            float alpha = Mathf.clamp(lifetime / 90f);
            float progress = 1f - (lifetime / 90f);
            float scale = 1f + Mathf.curve(progress, 0f, 0.2f) * 0.5f;
            
            boolean isDamage = amount < 0;
            Color color = isDamage ? Color.valueOf("ff6b6b") : Color.valueOf("84f491");
            String icon = isDamage ? "💥" : "💚";
            
            String text = icon + " " + (int)Math.abs(amount);
            
            Fonts.outline.getData().setScale(0.9f * scale);
            Fonts.outline.setColor(color.r, color.g, color.b, alpha);
            
            GlyphLayout layout = new GlyphLayout(Fonts.outline, text);
            
            Draw.color(Color.black, alpha * 0.7f);
            Fonts.outline.draw(text, x - layout.width/2f + 1f, y + 1f);
            
            Fonts.outline.setColor(color.r, color.g, color.b, alpha);
            Fonts.outline.draw(text, x - layout.width/2f, y);
            
            Fonts.outline.getData().setScale(1f);
            Draw.reset();
        }
    }
}