package tooltipsplus.ui;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Align;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.ui.Fonts;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.units.*;
import tooltipsplus.config.*;
import tooltipsplus.data.RangeData;

public class VisualIndicators {
    private Settings settings;
    private Seq<RangeData> rangeCache = new Seq<>();
    private float rangeCacheTimer = 0f;
    private float animationTimer = 0f;
    
    private static final float VISION_CONE_ANGLE = 60f;
    private static final int VISION_CONE_SEGMENTS = 20;
    
    public VisualIndicators(Settings settings) {
        this.settings = settings;
    }
    
    public void update() {
        animationTimer += Time.delta / 60f;
        
        if (settings.showHealthBars) {
            drawHealthIndicators();
        }
        
        if (settings.showRangeIndicators) {
            updateRangeCache();
            drawRangeIndicators();
        }
        
        if (settings.showEffectRanges) {
            drawEffectRanges();
        }
        
        if (settings.showVisionCones) {
            drawTurretVisionCones();
        }
    }
    
    void drawHealthIndicators() {
        Groups.unit.each(unit -> {
            if (!unit.isValid() || unit.dead || unit.health <= 0) return;
            
            float x = unit.x;
            float y = unit.y + unit.hitSize / 2f + Constants.HEALTH_BAR_OFFSET;
            
            drawHealthBar(x, y, unit.health, unit.maxHealth, unit.hitSize * 0.8f);
            
            if (settings.showShieldStacks && unit.shield > 0) {
                float shieldY = y + Constants.SHIELD_BAR_OFFSET - Constants.HEALTH_BAR_OFFSET;
                drawShieldBar(x, shieldY, unit.shield, unit.maxHealth, unit.hitSize * 0.8f);
                
                if (unit.shield > unit.maxHealth) {
                    int stacks = (int)(unit.shield / unit.maxHealth);
                    Fonts.outline.draw("x" + stacks, x + unit.hitSize * 0.5f, shieldY + 2f, Color.white, 0.5f, false, Align.left);
                }
            }
        });
        
        Groups.build.each(build -> {
            if (!build.isValid() || build.dead || build.health <= 0) return;
            
            float x = build.x;
            float y = build.y + build.block.size * 4f + Constants.HEALTH_BAR_OFFSET;
            float width = build.block.size * 7f;
            
            drawHealthBar(x, y, build.health, build.maxHealth, width);
        });
    }
    
    void drawHealthBar(float x, float y, float health, float maxHealth, float width) {
        float percent = Math.min(health / maxHealth, 1f);
        
        Draw.color(Color.black, 0.5f);
        Fill.rect(x, y, width + 2f, settings.healthBarHeight + 2f);
        
        Color barColor = percent > 0.6f ? Constants.HEALTH_COLOR : percent > 0.3f ? Color.yellow : Constants.DAMAGE_COLOR;
        Draw.color(barColor);
        Fill.rect(x - width / 2f + (width * percent) / 2f, y, width * percent, settings.healthBarHeight);
        
        Draw.reset();
    }
    
    void drawShieldBar(float x, float y, float shield, float maxHealth, float width) {
        float percent = Math.min(shield / maxHealth, 1f);
        
        Draw.color(Color.black, 0.5f);
        Fill.rect(x, y, width + 2f, settings.shieldBarHeight + 2f);
        
        Draw.color(Constants.SHIELD_COLOR, 0.8f);
        Fill.rect(x - width / 2f + (width * percent) / 2f, y, width * percent, settings.shieldBarHeight);
        
        Draw.reset();
    }
    
    void updateRangeCache() {
        rangeCacheTimer += Time.delta / 60f;
        if (rangeCacheTimer < Constants.RANGE_UPDATE) return;
        
        rangeCacheTimer = 0f;
        rangeCache.clear();
        
        if (settings.showUnitRanges) {
            Groups.unit.each(unit -> {
                if (!unit.isValid() || unit.dead || unit.health <= 0 || unit.type.weapons.size == 0) return;
                if (unit.team != Vars.player.team() && !settings.showTeamRanges) return;
                
                float maxRange = 0f;
                for (var weapon : unit.type.weapons) {
                    if (weapon.bullet != null) {
                        float weaponRange = weapon.bullet.rangeChange + weapon.bullet.speed * weapon.bullet.lifetime;
                        if (weaponRange > maxRange) {
                            maxRange = weaponRange;
                        }
                    }
                }
                
                if (maxRange > 0) {
                    Color color = unit.team == Vars.player.team() ? Constants.RANGE_ATTACK : Color.valueOf("ff9999");
                    rangeCache.add(new RangeData(unit.x, unit.y, maxRange, color, true));
                }
            });
        }
    }
    
    void drawRangeIndicators() {
        float pulseScale = settings.animateRanges ? 1f + Mathf.sin(animationTimer * 2f) * 0.05f : 1f;
        
        for (RangeData range : rangeCache) {
            float drawRange = range.isPulsing ? range.range * pulseScale : range.range;
            
            Draw.color(range.color, settings.rangeOpacity * 0.3f);
            Fill.circle(range.x, range.y, drawRange);
            
            Draw.color(range.color, settings.rangeOpacity * 1.5f);
            Lines.stroke(2f);
            Lines.circle(range.x, range.y, drawRange);
            
            Draw.reset();
        }
    }
    
    void drawTurretVisionCones() {
        Groups.build.each(build -> {
            if (!build.isValid() || build.dead || build.health <= 0) return;
            if (build.team != Vars.player.team() && !settings.showTeamRanges) return;
            
            if (build.block instanceof Turret) {
                Turret turret = (Turret)build.block;
                float rotation = build.rotation;
                
                boolean hasTarget = false;
                boolean isFiring = false;
                
                if (build instanceof Turret.TurretBuild) {
                    Turret.TurretBuild tb = (Turret.TurretBuild)build;
                    hasTarget = tb.target != null;
                    isFiring = tb.isShooting();
                }
                
                Color coneColor;
                float alpha;
                
                if (isFiring) {
                    coneColor = Color.valueOf("ff4444");
                    alpha = 0.4f + Mathf.absin(animationTimer * 5f, 0.2f);
                } else if (hasTarget) {
                    coneColor = Color.valueOf("ffaa44");
                    alpha = 0.35f;
                } else {
                    coneColor = Color.valueOf("44ff44");
                    alpha = 0.25f;
                }
                
                drawVisionCone(build.x, build.y, rotation, turret.range, coneColor, alpha);
            }
        });
    }
    
    void drawVisionCone(float x, float y, float rotation, float range, Color color, float alpha) {
        float halfAngle = VISION_CONE_ANGLE / 2f;
        
        Draw.color(color, alpha * settings.rangeOpacity);
        
        Fill.tri(
            x, y,
            x + Mathf.cosDeg(rotation - halfAngle) * range,
            y + Mathf.sinDeg(rotation - halfAngle) * range,
            x + Mathf.cosDeg(rotation + halfAngle) * range,
            y + Mathf.sinDeg(rotation + halfAngle) * range
        );
        
        for (int i = 0; i < VISION_CONE_SEGMENTS; i++) {
            float angle1 = rotation - halfAngle + (VISION_CONE_ANGLE * i / VISION_CONE_SEGMENTS);
            float angle2 = rotation - halfAngle + (VISION_CONE_ANGLE * (i + 1) / VISION_CONE_SEGMENTS);
            
            Fill.tri(
                x, y,
                x + Mathf.cosDeg(angle1) * range,
                y + Mathf.sinDeg(angle1) * range,
                x + Mathf.cosDeg(angle2) * range,
                y + Mathf.sinDeg(angle2) * range
            );
        }
        
        Draw.color(color, alpha * settings.rangeOpacity * 2f);
        Lines.stroke(2f);
        Lines.arc(x, y, range, VISION_CONE_ANGLE / 360f, rotation - halfAngle);
        
        Lines.line(x, y, x + Mathf.cosDeg(rotation - halfAngle) * range, y + Mathf.sinDeg(rotation - halfAngle) * range);
        Lines.line(x, y, x + Mathf.cosDeg(rotation + halfAngle) * range, y + Mathf.sinDeg(rotation + halfAngle) * range);
        
        Draw.reset();
    }
    
    void drawEffectRanges() {
        Groups.build.each(build -> {
            if (!build.isValid() || build.dead || build.health <= 0) return;
            if (build.team != Vars.player.team()) return;
            
            Color effectColor = null;
            float range = 0f;
            
            if (build.block.name.contains("mend")) {
                effectColor = Constants.RANGE_REPAIR;
                range = 60f;
            } else if (build.block.name.contains("overdrive")) {
                effectColor = Constants.RANGE_EFFECT;
                range = 80f;
            } else if (build.block instanceof RepairTurret) {
                RepairTurret rt = (RepairTurret)build.block;
                effectColor = Constants.RANGE_REPAIR;
                range = rt.repairRadius;
            } else if (build.block instanceof UnitFactory) {
                effectColor = Color.cyan;
                range = 40f;
            }
            
            if (effectColor != null && range > 0) {
                float pulseAlpha = settings.animateRanges ? 
                    settings.effectRangeOpacity * (1f + Mathf.sin(animationTimer * 3f) * 0.3f) : 
                    settings.effectRangeOpacity;
                
                Draw.color(effectColor, pulseAlpha);
                Fill.circle(build.x, build.y, range);
                
                Draw.color(effectColor, pulseAlpha * 2f);
                Lines.stroke(1.5f);
                Lines.dashCircle(build.x, build.y, range);
                
                Draw.reset();
            }
        });
    }
}