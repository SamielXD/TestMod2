package tooltipsplus.ui;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.math.Interp;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
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
    private Map<Integer, HealthCache> healthCache = new HashMap<>();
    
    private static final Pool<DamageNumber> damagePool = Pools.get(DamageNumber.class, DamageNumber::new);
    
    public HealthDisplaySystem(Settings settings) {
        this.settings = settings;
    }
    
    public void draw() {
        if (settings.showHealthBars) {
            Groups.unit.each(unit -> {
                if (!unit.isValid() || unit.dead || unit.health <= 0) return;
                updateHealthCache(unit);
                drawUnitHealth(unit);
                if (settings.showStatusEffects) drawUnitStatus(unit);
            });
            
            Groups.build.each(build -> {
                if (!build.isValid() || build.dead || build.health <= 0) return;
                updateHealthCache(build);
                drawBuildingHealth(build);
            });
        }
        
        if (settings.showDamageNumbers) {
            updateDamageNumbers();
        }
    }
    
    void updateHealthCache(Healthc entity) {
        int id = entity.id();
        float currentHealth = entity.health();
        float currentShield = entity instanceof Unit ? ((Unit)entity).shield : 0f;
        
        HealthCache cache = healthCache.get(id);
        if (cache == null) {
            cache = new HealthCache();
            cache.health = currentHealth;
            cache.displayHealth = currentHealth;
            cache.shield = currentShield;
            cache.displayShield = currentShield;
            healthCache.put(id, cache);
            return;
        }
        
        float healthDiff = currentHealth - cache.health;
        if (Math.abs(healthDiff) > 0.5f) {
            boolean shouldShow = settings.showAllDamageNumbers;
            if (!shouldShow && entity instanceof Teamc) {
                shouldShow = ((Teamc)entity).team() == Vars.player.team();
            }
            
            if (shouldShow) {
                float x = 0, y = 0;
                if (entity instanceof Posc) {
                    Posc pos = (Posc)entity;
                    x = pos.x();
                    y = pos.y();
                    if (entity instanceof Hitboxc) {
                        y += ((Hitboxc)entity).hitSize() / 2f + 25f;
                    }
                }
                spawnDamageNumber(x, y, healthDiff);
            }
            cache.health = currentHealth;
        }
        
        cache.displayHealth = Mathf.lerp(cache.displayHealth, currentHealth, 0.15f);
        cache.displayShield = Mathf.lerp(cache.displayShield, currentShield, 0.15f);
    }
    
    void spawnDamageNumber(float x, float y, float amount) {
        DamageNumber dn = damagePool.obtain();
        dn.set(x, y, amount, settings.damageNumberScale);
        damageNumbers.add(dn);
    }
    
    void updateDamageNumbers() {
        damageNumbers.each(dn -> {
            dn.update();
            dn.draw();
        });
        damageNumbers.removeAll(dn -> {
            if (dn.lifetime <= 0) {
                damagePool.free(dn);
                return true;
            }
            return false;
        });
    }
    
    void drawUnitHealth(Unit unit) {
        int id = unit.id();
        HealthCache cache = healthCache.get(id);
        if (cache == null) return;
        
        float x = unit.x;
        float y = unit.y + unit.hitSize / 2f + 16f * settings.healthBarScale;
        float width = Math.max(45f, unit.hitSize * 1.2f) * settings.healthBarScale;
        
        Color barColor = getTeamColor(unit.team);
        drawHealthBar(x, y, cache.displayHealth, unit.maxHealth, cache.displayShield, width, barColor);
    }
    
    void drawUnitStatus(Unit unit) {
        float x = unit.x;
        float y = unit.y + unit.hitSize / 2f + 28f * settings.healthBarScale;
        
        Seq<StatusEffect> activeEffects = new Seq<>();
        Vars.content.statusEffects().each(effect -> {
            if (unit.hasEffect(effect) && effect.uiIcon != null) {
                activeEffects.add(effect);
            }
        });
        
        if (activeEffects.isEmpty()) return;
        
        float iconSize = 14f * settings.healthBarScale;
        float totalWidth = activeEffects.size * (iconSize + 2f);
        float startX = x - totalWidth / 2f;
        
        for (int i = 0; i < activeEffects.size; i++) {
            StatusEffect effect = activeEffects.get(i);
            float iconX = startX + i * (iconSize + 2f) + iconSize/2f;
            
            Draw.color(Color.white, 0.9f);
            Draw.rect(effect.uiIcon, iconX, y, iconSize, iconSize);
            
            if (!effect.permanent) {
                float time = unit.getDuration(effect);
                String timeStr = formatTime(time);
                
                Fonts.outline.getData().setScale(0.35f * settings.healthBarScale);
                Fonts.outline.setColor(Pal.accent);
                Fonts.outline.draw(timeStr, iconX - 2f, y - iconSize/2f - 1f);
                Fonts.outline.getData().setScale(1f);
            }
        }
        
        Draw.reset();
    }
    
    String formatTime(float ticks) {
        float seconds = ticks / 60f;
        if (seconds > 60) {
            int minutes = (int)(seconds / 60);
            if (minutes > 60) return (minutes / 60) + "h";
            return minutes + "m";
        }
        return (int)seconds + "s";
    }
    
    void drawBuildingHealth(Building build) {
        int id = build.id();
        HealthCache cache = healthCache.get(id);
        if (cache == null) return;
        
        float x = build.x;
        float y = build.y + build.block.size * 4f + 16f * settings.healthBarScale;
        float width = Math.max(45f, build.block.size * 7f) * settings.healthBarScale;
        
        Color barColor = getTeamColor(build.team);
        drawHealthBar(x, y, cache.displayHealth, build.maxHealth, 0f, width, barColor);
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
        
        float barHeight = 7f * settings.healthBarScale;
        float shieldHeight = 5f * settings.healthBarScale;
        
        drawSmoothBar(x, y, width, barHeight, healthPercent, teamColor);
        
        if (shieldStacks > 0) {
            float shieldY = y + barHeight/2f + shieldHeight/2f + 1.5f;
            Color shieldColor = getShieldColor(shieldStacks, teamColor);
            
            drawSmoothBar(x, shieldY, width, shieldHeight, shieldPercent, shieldColor);
            
            if (shieldStacks > 1) {
                Fonts.outline.getData().setScale(0.5f * settings.healthBarScale);
                Fonts.outline.setColor(getShieldStackTextColor(shieldStacks));
                Fonts.outline.draw("x" + shieldStacks, x + width/2f + 3f, shieldY + 1.5f);
                Fonts.outline.getData().setScale(1f);
            }
        }
        
        String healthText = (int)health + "/" + (int)maxHealth;
        Fonts.outline.getData().setScale(0.48f * settings.healthBarScale);
        Fonts.outline.setColor(Color.white);
        
        GlyphLayout layout = new GlyphLayout(Fonts.outline, healthText);
        Fonts.outline.draw(healthText, x - layout.width/2f, y + 2.5f);
        Fonts.outline.getData().setScale(1f);
        
        Draw.reset();
    }
    
    void drawSmoothBar(float x, float y, float width, float height, float percent, Color baseColor) {
        Draw.color(0, 0, 0, 0.75f);
        Fill.rect(x, y, width + 3f, height + 3f);
        
        Draw.color(0.2f, 0.2f, 0.2f, 0.6f);
        Fill.rect(x, y, width, height);
        
        float fillWidth = width * percent;
        
        Draw.color(baseColor.r * 0.6f, baseColor.g * 0.6f, baseColor.b * 0.6f, 0.8f);
        Fill.rect(x - width/2f + fillWidth/2f, y, fillWidth, height);
        
        Draw.color(baseColor, 0.9f);
        Fill.rect(x - width/2f + fillWidth/2f, y, fillWidth, height * 0.7f);
        
        float pulse = Mathf.absin(Time.time / 60f * 5f, 0.15f);
        Draw.color(Color.white, 0.25f + pulse);
        Fill.rect(x - width/2f + fillWidth/2f, y + height/2f - 1f, fillWidth, 1.5f);
        
        Draw.reset();
    }
    
    Color getShieldColor(int stacks, Color teamColor) {
        int cycle = (stacks - 1) % 3;
        switch(cycle) {
            case 0: return Pal.heal;
            case 1: return Pal.accent;
            case 2: return Pal.reactorPurple;
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
    
    static class HealthCache {
        float health, displayHealth;
        float shield, displayShield;
    }
    
    static class DamageNumber implements Pool.Poolable {
        float x, y, amount;
        float lifetime = 60f;
        float scale = 1f;
        float startScale = 0f;
        
        void set(float x, float y, float amount, float sizeScale) {
            this.x = x + Mathf.range(6f);
            this.y = y;
            this.amount = amount;
            this.lifetime = 60f;
            this.scale = sizeScale;
            this.startScale = 0f;
        }
        
        void update() {
            lifetime -= Time.delta;
            startScale = Mathf.lerp(startScale, 1f, 0.2f);
            y += Time.delta * 0.5f;
        }
        
        void draw() {
            float alpha = Mathf.clamp(lifetime / 60f);
            float pop = Interp.elasticOut.apply(Mathf.clamp(startScale));
            float finalScale = (0.8f + pop * 0.4f) * scale;
            
            boolean isDamage = amount < 0;
            Color color = isDamage ? Color.valueOf("ff5555") : Color.valueOf("55ff55");
            String prefix = isDamage ? "-" : "+";
            String text = prefix + (int)Math.abs(amount);
            
            Fonts.outline.getData().setScale(finalScale);
            Fonts.outline.setColor(color.r, color.g, color.b, alpha);
            
            GlyphLayout layout = new GlyphLayout(Fonts.outline, text);
            Fonts.outline.draw(text, x - layout.width/2f, y);
            
            Fonts.outline.getData().setScale(1f);
            Draw.reset();
        }
        
        @Override
        public void reset() {
            x = y = amount = 0;
            lifetime = 60f;
            scale = 1f;
            startScale = 0f;
        }
    }
}