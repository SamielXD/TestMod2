package tooltipsplus.ui;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
import mindustry.ui.Fonts;
import tooltipsplus.config.Settings;
import java.util.HashMap;
import java.util.Map;

public class HealthDisplaySystem {
    private Settings settings;
    private Seq<DamageNumber> damageNumbers = new Seq<>();
    private Map<Integer, Float> lastHealthMap = new HashMap<>();
    private float updateTimer = 0f;
    
    public HealthDisplaySystem(Settings settings) {
        this.settings = settings;
    }
    
    public void draw() {
        updateTimer += Time.delta;
        boolean shouldUpdate = updateTimer >= 2f;
        if (shouldUpdate) updateTimer = 0f;
        
        if (settings.showHealthBars) {
            Groups.unit.each(unit -> {
                if (!unit.isValid() || unit.dead || unit.health <= 0) return;
                if (shouldUpdate) checkDamage(unit);
                drawUnitHealth(unit);
                if (settings.showStatusEffects) drawUnitStatus(unit);
            });
            
            Groups.build.each(build -> {
                if (!build.isValid() || build.dead || build.health <= 0) return;
                if (shouldUpdate) checkDamage(build);
                drawBuildingHealth(build);
            });
        }
        
        updateDamageNumbers();
    }
    
    void checkDamage(Healthc entity) {
        int id = entity.id();
        float currentHealth = entity.health();
        
        if (lastHealthMap.containsKey(id)) {
            float lastHealth = lastHealthMap.get(id);
            float diff = currentHealth - lastHealth;
            
            if (Math.abs(diff) > 0.5f) {
                boolean shouldShow = settings.showAllDamageNumbers;
                
                if (!shouldShow && entity instanceof Teamc) {
                    Teamc teamEntity = (Teamc)entity;
                    shouldShow = teamEntity.team() == Vars.player.team();
                }
                
                if (shouldShow) {
                    float x = 0, y = 0;
                    if (entity instanceof Posc) {
                        Posc pos = (Posc)entity;
                        x = pos.x();
                        y = pos.y();
                        if (entity instanceof Hitboxc) {
                            y += ((Hitboxc)entity).hitSize() / 2f + 30f;
                        }
                    }
                    spawnDamageNumber(x, y, diff);
                }
            }
        }
        
        lastHealthMap.put(id, currentHealth);
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
        float y = unit.y + unit.hitSize / 2f + 18f * settings.healthBarScale;
        float width = Math.max(50f, unit.hitSize * 1.3f) * settings.healthBarScale;
        
        Color barColor = getTeamColor(unit.team);
        drawHealthBar(x, y, unit.health, unit.maxHealth, unit.shield, width, barColor);
    }
    
    void drawUnitStatus(Unit unit) {
        float x = unit.x;
        float y = unit.y + unit.hitSize / 2f + 32f * settings.healthBarScale;
        
        Seq<StatusEffect> activeEffects = new Seq<>();
        Vars.content.statusEffects().each(effect -> {
            if (unit.hasEffect(effect) && effect.uiIcon != null) {
                activeEffects.add(effect);
            }
        });
        
        if (activeEffects.isEmpty()) return;
        
        float iconSize = 16f * settings.healthBarScale;
        float totalWidth = activeEffects.size * (iconSize + 3f) - 3f;
        float startX = x - totalWidth / 2f;
        
        for (int i = 0; i < activeEffects.size; i++) {
            StatusEffect effect = activeEffects.get(i);
            float iconX = startX + i * (iconSize + 3f);
            
            Draw.color(Color.black, 0.7f);
            Fill.rect(iconX + iconSize/2f, y, iconSize + 2f, iconSize + 2f);
            
            Draw.color(Color.white);
            Draw.rect(effect.uiIcon, iconX + iconSize/2f, y, iconSize, iconSize);
            
            if (!effect.permanent) {
                float time = unit.getDuration(effect);
                String timeStr = formatTime(time);
                
                Fonts.outline.getData().setScale(0.4f * settings.healthBarScale);
                Fonts.outline.setColor(Pal.accent);
                Fonts.outline.draw(timeStr, iconX, y - iconSize/2f - 2f);
                Fonts.outline.getData().setScale(1f);
            }
        }
        
        Draw.reset();
    }
    
    String formatTime(float ticks) {
        float seconds = ticks / 60f;
        if (seconds > 60) {
            int minutes = (int)(seconds / 60);
            if (minutes > 60) {
                int hours = minutes / 60;
                return hours + "h";
            }
            return minutes + "m";
        }
        return (int)seconds + "s";
    }
    
    void drawBuildingHealth(Building build) {
        float x = build.x;
        float y = build.y + build.block.size * 4f + 18f * settings.healthBarScale;
        float width = Math.max(50f, build.block.size * 8f) * settings.healthBarScale;
        
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
        
        float barHeight = 8f * settings.healthBarScale;
        float shieldHeight = 6f * settings.healthBarScale;
        
        drawGradientBar(x, y, width, barHeight, healthPercent, teamColor, true);
        
        if (shieldStacks > 0) {
            float shieldY = y + barHeight/2f + shieldHeight/2f + 2f;
            Color shieldColor = getShieldColor(shieldStacks, teamColor);
            
            drawGradientBar(x, shieldY, width, shieldHeight, shieldPercent, shieldColor, false);
            
            if (shieldStacks > 1) {
                String stackText = "x" + shieldStacks;
                Color textColor = getShieldStackTextColor(shieldStacks);
                
                Fonts.outline.getData().setScale(0.6f * settings.healthBarScale);
                Fonts.outline.setColor(textColor);
                Fonts.outline.draw(stackText, x + width/2f + 4f, shieldY + 2f);
                Fonts.outline.getData().setScale(1f);
            }
            
            Fonts.outline.getData().setScale(0.6f * settings.healthBarScale);
            Fonts.outline.setColor(Color.white);
            Fonts.outline.draw("\uE84D", x - width/2f - 8f, shieldY + 2f);
            Fonts.outline.getData().setScale(1f);
        }
        
        String healthText = (int)health + "/" + (int)maxHealth;
        Fonts.outline.getData().setScale(0.55f * settings.healthBarScale);
        Fonts.outline.setColor(Color.white);
        
        GlyphLayout layout = new GlyphLayout(Fonts.outline, healthText);
        Fonts.outline.draw(healthText, x - layout.width/2f, y + 3f);
        Fonts.outline.getData().setScale(1f);
        
        Draw.reset();
    }
    
    void drawGradientBar(float x, float y, float width, float height, float percent, Color baseColor, boolean isHealth) {
        Draw.color(Color.black, 0.85f);
        Fill.rect(x, y, width + 4f, height + 4f);
        
        Draw.color(Color.darkGray, 0.7f);
        Fill.rect(x, y, width, height);
        
        float fillWidth = width * percent;
        
        for (int i = 0; i < 6; i++) {
            float layer = i / 6f;
            float alpha = 0.25f + (0.75f * (1f - layer));
            float yOffset = (height / 2f) * layer;
            
            Draw.color(baseColor, alpha);
            Fill.rect(x - width/2f + fillWidth/2f, y - height/2f + yOffset, fillWidth, height - yOffset * 2f);
        }
        
        Draw.color(baseColor.cpy().lerp(Color.white, 0.5f), 0.95f);
        Lines.stroke(2f);
        Lines.rect(x - width/2f, y - height/2f, fillWidth, height);
        
        float pulse = Mathf.absin(Time.time / 60f * 4f, 0.2f);
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
        float velY = 0.8f;
        
        DamageNumber(float x, float y, float amount) {
            this.x = x + Mathf.range(8f);
            this.y = y;
            this.amount = amount;
        }
        
        void update() {
            lifetime -= Time.delta;
            y += velY * Time.delta * settings.damageNumberScale;
            velY *= 0.98f;
        }
        
        void draw() {
            float alpha = Mathf.clamp(lifetime / 90f);
            float progress = 1f - (lifetime / 90f);
            float scale = (0.6f + Mathf.curve(progress, 0f, 0.25f) * 0.3f) * settings.damageNumberScale;
            
            boolean isDamage = amount < 0;
            Color color = isDamage ? Color.valueOf("ff4444") : Color.valueOf("44ff44");
            String prefix = isDamage ? "-" : "+";
            
            String text = prefix + (int)Math.abs(amount);
            
            Fonts.outline.getData().setScale(scale);
            
            Draw.color(Color.black, alpha * 0.8f);
            Fonts.outline.setColor(0, 0, 0, alpha * 0.8f);
            Fonts.outline.draw(text, x + 1.5f, y + 1.5f);
            
            Fonts.outline.setColor(color.r, color.g, color.b, alpha);
            Fonts.outline.draw(text, x, y);
            
            Fonts.outline.getData().setScale(1f);
            Draw.reset();
        }
    }
}