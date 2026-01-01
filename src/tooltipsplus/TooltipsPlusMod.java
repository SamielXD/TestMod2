package tooltipsplus;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.defense.*;

/**
 * TooltipsPlus - simple, robust tooltip enhancer for Mindustry 1.5.4+
 * - Appends extra info into block/unit/item/liquid descriptions (shown in info dialog).
 * - Provides a small Settings category to toggle features.
 *
 * Notes:
 * - Uses description/details strings instead of fragile Stats APIs (avoids version mismatches).
 * - Adds a marker "[Tooltips+]" so the mod won't duplicate text on reloads.
 */
public class TooltipsPlusMod extends Mod {

    boolean enabled = true;
    boolean showTemperature = true;
    boolean showItemFlags = true;
    boolean debug = false;

    @Override
    public void init() {
        Log.info("TooltipsPlus initializing...");
        loadSettings();

        // ClientLoadEvent preferred for UI operations; also inject on init so server/headless builds get basic changes.
        Events.on(ClientLoadEvent.class, e -> {
            Core.app.post(() -> {
                if (enabled) injectTooltips();
                addSettingsUI();
                Log.info("TooltipsPlus loaded (Client).");
            });
        });

        // Also do a fallback injection at init so headless/build-time content gets annotated:
        if (enabled) injectTooltips();
    }

    void loadSettings() {
        try {
            enabled = Core.settings.getBool("tooltipsplus-enabled", true);
            showTemperature = Core.settings.getBool("tooltipsplus-temp", true);
            showItemFlags = Core.settings.getBool("tooltipsplus-itemflags", true);
            debug = Core.settings.getBool("tooltipsplus-debug", false);
        } catch (Exception ex) {
            Log.err("TooltipsPlus: failed to load settings", ex);
        }
    }

    void saveSettings() {
        Core.settings.put("tooltipsplus-enabled", enabled);
        Core.settings.put("tooltipsplus-temp", showTemperature);
        Core.settings.put("tooltipsplus-itemflags", showItemFlags);
        Core.settings.put("tooltipsplus-debug", debug);
        Core.settings.forceSave();
    }

    void injectTooltips() {
        int b = 0, u = 0, i = 0, l = 0;

        for (Block block : Vars.content.blocks()) {
            if (enhanceBlock(block)) b++;
        }
        for (UnitType unit : Vars.content.units()) {
            if (enhanceUnit(unit)) u++;
        }
        for (Item item : Vars.content.items()) {
            if (enhanceItem(item)) i++;
        }
        for (Liquid liquid : Vars.content.liquids()) {
            if (enhanceLiquid(liquid)) l++;
        }

        Log.info("TooltipsPlus: enhanced @ blocks, @ units, @ items, @ liquids", b, u, i, l);
    }

    boolean enhanceBlock(Block block) {
        try {
            String marker = "[Tooltips+]";
            String orig = block.description == null ? "" : block.description;
            if (orig.contains(marker)) return false; // already enhanced

            StringBuilder sb = new StringBuilder(orig);
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(marker).append("\n");

            // Add health if present
            if (block.health > 0) {
                sb.append("[stat]Health:[] ").append((int) block.health).append("\n");
            }

            // Items
            if (block.hasItems && block.itemCapacity > 0) {
                sb.append("[stat]Item capacity:[] ").append(block.itemCapacity).append("\n");
            }

            // Liquids
            if (block.hasLiquids && block.liquidCapacity > 0) {
                // liquidCapacity is float; round sensibly
                sb.append("[stat]Liquid cap:[] ").append(Strings.autoFixed(block.liquidCapacity, 1)).append("\n");
            }

            // Power generation (generator types)
            if (block instanceof PowerGenerator) {
                PowerGenerator gen = (PowerGenerator) block;
                if (gen.powerProduction > 0f) {
                    float perSec = gen.powerProduction * 60f;
                    sb.append("[stat]Power:[] +").append(Strings.autoFixed(perSec, 1)).append("/s").append("\n");
                }
            }

            // Size
            if (block.size > 1) {
                sb.append("[stat]Size:[] ").append(block.size).append("x").append(block.size).append("\n");
            }

            // Build cost: sum of requirements (if present)
            try {
                if (block.requirements != null && block.requirements.length > 0) {
                    int totalCost = 0;
                    for (int idx = 0; idx < block.requirements.length; idx++) {
                        totalCost += block.requirements[idx].amount;
                    }
                    sb.append("[stat]Total cost:[] ").append(totalCost).append("\n");
                }
            } catch (Throwable t) {
                // ignore if requirement API differs
            }

            // Set description + details (details used by some clients)
            block.description = sb.toString();
            block.details = sb.toString();
            if (debug) Log.info("TooltipsPlus: enhanced block @", block.name);
            return true;
        } catch (Throwable ex) {
            if (debug) Log.err("TooltipsPlus: failed to enhance block @", ex, block.name);
            return false;
        }
    }

    boolean enhanceUnit(UnitType unit) {
        try {
            String marker = "[Tooltips+]";
            String orig = unit.description == null ? "" : unit.description;
            if (orig.contains(marker)) return false;

            StringBuilder sb = new StringBuilder(orig);
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(marker).append("\n");

            sb.append("[stat]Health:[] ").append((int) unit.health).append("\n");
            sb.append("[stat]Speed:[] ").append(Strings.autoFixed(unit.speed, 1)).append("\n");
            sb.append("[stat]Armor:[] ").append((int) unit.armor).append("\n");

            if (unit.flying) sb.append("[accent]Flying[]\n");
            if (unit.naval) sb.append("[accent]Naval[]\n");
            if (unit.mineSpeed > 0) sb.append("[accent]Can mine[]\n");
            if (unit.buildSpeed > 0) sb.append("[accent]Can build[]\n");

            unit.description = sb.toString();
            unit.details = sb.toString();
            if (debug) Log.info("TooltipsPlus: enhanced unit @", unit.name);
            return true;
        } catch (Throwable ex) {
            if (debug) Log.err("TooltipsPlus: failed to enhance unit @", ex, unit.name);
            return false;
        }
    }

    boolean enhanceItem(Item item) {
        try {
            String marker = "[Tooltips+]";
            String orig = item.description == null ? "" : item.description;
            if (orig.contains(marker)) return false;

            StringBuilder sb = new StringBuilder(orig);
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(marker).append("\n");

            // add flags if enabled
            if (showItemFlags) {
                if (item.hardness > 0) sb.append("[stat]Hardness:[] ").append(item.hardness).append("\n");
                if (item.flammability > 0.1f) sb.append("[orange]Flammable[]\n");
                if (item.explosiveness > 0.1f) sb.append("[scarlet]Explosive[]\n");
                if (item.radioactivity > 0.1f) sb.append("[green]Radioactive[]\n");
            }

            item.description = sb.toString();
            item.details = sb.toString();
            if (debug) Log.info("TooltipsPlus: enhanced item @", item.name);
            return true;
        } catch (Throwable ex) {
            if (debug) Log.err("TooltipsPlus: failed to enhance item @", ex, item.name);
            return false;
        }
    }

    boolean enhanceLiquid(Liquid liquid) {
        try {
            String marker = "[Tooltips+]";
            String orig = liquid.description == null ? "" : liquid.description;
            if (orig.contains(marker)) return false;

            StringBuilder sb = new StringBuilder(orig);
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(marker).append("\n");

            if (showTemperature) {
                sb.append("[stat]Temp:[] ").append(Strings.autoFixed(liquid.temperature, 1)).append("\n");
            }
            if (liquid.flammability > 0.1f) sb.append("[orange]Flammable[]\n");
            if (liquid.explosiveness > 0.1f) sb.append("[scarlet]Explosive[]\n");

            liquid.description = sb.toString();
            liquid.details = sb.toString();
            if (debug) Log.info("TooltipsPlus: enhanced liquid @", liquid.name);
            return true;
        } catch (Throwable ex) {
            if (debug) Log.err("TooltipsPlus: failed to enhance liquid @", ex, liquid.name);
            return false;
        }
    }

    void addSettingsUI() {
        try {
            // add a small settings page under the main Settings UI
            Vars.ui.settings.addCategory("Tooltips+", Icon.book, table -> {
                table.row();
                table.checkPref("tooltipsplus-enabled", enabled, v -> {
                    enabled = v;
                    saveSettings();
                    // toggling requires restart or re-injection
                    if (enabled) {
                        injectTooltips();
                        Vars.ui.showInfo("[lime]Tooltips+ enabled (changes applied).");
                    } else {
                        Vars.ui.showInfo("[scarlet]Tooltips+ disabled (restart may be required).");
                    }
                });
                table.row();

                table.checkPref("tooltipsplus-temp", showTemperature, v -> {
                    showTemperature = v;
                    saveSettings();
                    Vars.ui.showInfo("[cyan]Temperature display: " + (v ? "ON" : "OFF"));
                });
                table.row();

                table.checkPref("tooltipsplus-itemflags", showItemFlags, v -> {
                    showItemFlags = v;
                    saveSettings();
                    Vars.ui.showInfo("[cyan]Item flags: " + (v ? "ON" : "OFF"));
                });
                table.row();

                table.checkPref("tooltipsplus-debug", debug, v -> {
                    debug = v;
                    saveSettings();
                    Vars.ui.showInfo("[cyan]Debug: " + (v ? "ON" : "OFF"));
                });
                table.row();

                table.add("[lightgray]Tooltips+ appends extra info into mod/game info dialogs.").padTop(8f).left();
            });
        } catch (Throwable ex) {
            Log.err("TooltipsPlus: failed to add settings UI", ex);
        }
    }
}