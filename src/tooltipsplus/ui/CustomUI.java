package tooltipsplus.ui;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.Vec2;
import mindustry.gen.Groups;
import mindustry.ui.Fonts;
import tooltipsplus.config.*;

public class CustomUI {
    private Settings settings;
    
    public CustomUI(Settings settings) {
        this.settings = settings;
    }
    
    public void drawCustomHealthBars() {
        Groups.unit.each(unit -> {
            if (!unit.isValid() || unit.dead || unit.health <= 0) return;
            
            float x = unit.x;
            float y = unit.y + unit.hitSize / 2f + 15f;
            float width = unit.hitSize * 1.2f;
            float height = 8f;
            float percent = unit.health / unit.maxHealth;
            
            drawRoundedHealthBar(x, y, width, height, percent);
            
            if (unit.shield > 0) {
                float shieldPercent = Math.min(unit.shield / unit.maxHealth, 1f);
                drawShieldBar(x, y + 10f, width, 5f, shieldPercent);
            }
        });
    }
    
    void drawRoundedHealthBar(float x, float y, float width, float height, float percent) {
        float radius = height / 2f;
        
        Draw.color(Color.black, 0.6f);
        Fill.rect(x, y, width + 2f, height + 2f, radius);
        
        Color barColor = percent > 0.6f ? Color.valueOf("84f491") : 
                        percent > 0.3f ? Color.valueOf("f4d03f") : 
                        Color.valueOf("ff6b6b");
        
        Draw.color(barColor);
        float filledWidth = width * percent;
        Fill.rect(x - width/2f + filledWidth/2f, y, filledWidth, height, radius);
        
        Draw.color(Color.white, 0.3f);
        Lines.stroke(1f);
        Lines.rect(x - width/2f, y - height/2f, width, height, radius);
        
        Draw.reset();
    }
    
    void drawShieldBar(float x, float y, float width, float height, float percent) {
        Draw.color(Color.valueOf("84f491"), 0.3f);
        Fill.rect(x, y, width, height);
        
        Draw.color(Color.valueOf("84f491"), 0.8f);
        Fill.rect(x - width/2f + (width * percent)/2f, y, width * percent, height);
        
        Draw.reset();
    }
    
    public void drawResourceCounter() {
        // Coming soon - will show resources like in Helium
    }
}