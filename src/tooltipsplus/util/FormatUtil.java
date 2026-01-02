package tooltipsplus.util;

import arc.util.Strings;

public class FormatUtil {
    public static String formatNumber(float num) {
        if (num >= 1000000) {
            return Strings.autoFixed(num / 1000000f, 1) + "M";
        } else if (num >= 1000) {
            return Strings.autoFixed(num / 1000f, 1) + "K";
        }
        return Strings.autoFixed(num, 1);
    }
    
    public static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    public static String makeProgressBar(float current, float max, int width) {
        int filled = (int)((current / max) * width);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < width; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("]");
        return bar.toString();
    }
}