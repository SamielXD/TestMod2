package tooltipsplus.data;

import arc.graphics.Color;

public class RangeData {
    public float x, y, range;
    public Color color;
    public boolean isPulsing;
    
    public RangeData(float x, float y, float range, Color color, boolean pulse) {
        this.x = x;
        this.y = y;
        this.range = range;
        this.color = color;
        this.isPulsing = pulse;
    }
}
