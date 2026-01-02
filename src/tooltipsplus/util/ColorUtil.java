package tooltipsplus.util;

public class ColorUtil {
    public String statColor = "[stat]";
    public String accentColor = "[accent]";
    public String warningColor = "[scarlet]";
    public String successColor = "[lime]";
    public String infoColor = "[lightgray]";
    
    public void applyTheme(String theme) {
        switch(theme) {
            case "dark":
                statColor = "[gray]";
                accentColor = "[lightgray]";
                warningColor = "[orange]";
                successColor = "[green]";
                infoColor = "[darkgray]";
                break;
            case "neon":
                statColor = "[cyan]";
                accentColor = "[magenta]";
                warningColor = "[red]";
                successColor = "[lime]";
                infoColor = "[sky]";
                break;
            case "minimal":
                statColor = "";
                accentColor = "";
                warningColor = "[scarlet]";
                successColor = "[lime]";
                infoColor = "[lightgray]";
                break;
            default:
                statColor = "[stat]";
                accentColor = "[accent]";
                warningColor = "[scarlet]";
                successColor = "[lime]";
                infoColor = "[lightgray]";
                break;
        }
    }
    
    public String getPercentColor(float percent) {
        if (percent > 75f) return successColor;
        if (percent > 50f) return "[yellow]";
        if (percent > 25f) return "[orange]";
        return warningColor;
    }
}
