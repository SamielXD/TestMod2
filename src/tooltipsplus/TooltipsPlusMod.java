package tooltipsplus;

import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Events;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.ui.Styles.Icon;
import mindustry.ui.Icons;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * TooltipsPlusMod
 * - Adds extra stats to tooltips for blocks, units, items, liquids.
 * - Provides a simple Settings page in Options -> Tooltips+
 *
 * Notes:
 * - Avoids non-existent API members (stats.initialized, stats.has(...), Stat.hardness, etc).
 * - Uses Stat.input for custom textual entries when needed.
 */
public class TooltipsPlusMod extends Mod {

    boolean enabled = true;
    boolean showTemperature = true;
    boolean showItemFlags = true;
    boolean debug = false;

    @Override
    public void init() {
        Log.info("Tooltips+ initializing...");
        loadSettings();

        // run on client load to ensure UI is available
        Events.on(ClientLoadEvent.class, e -> {
            try {
                // Post to main app thread for UI / content access
                Vars.ui.getStage().post(() -> {
                    try {
                        injectTooltips();
                    } catch (Throwable t) {
                        Log.err("Tooltips+: injectTooltips failed", t);
                    }
                    try {
                        addSettings();
                    } catch (Throwable t) {
                        Log.err("Tooltips+: addSettings failed", t);
                    }
                });
            } catch (Throwable t) {
                Log.err("Tooltips+: clientload handler failed", t);
            }
        });

        // optional: save logs on world load (similar to your JS snippet)
        Events.on(WorldLoadEvent.class, e -> {
            if (enabled) {
                // example action: schedule a small confirmation toast
                Timer.schedule(() -> Vars.ui.showInfoToast("[lime]Tooltips+ active", 1.5f), 0.5f);
            }
        });

        Log.info("Tooltips+ initialized");
    }

    void loadSettings() {
        enabled = Core.settings.getBool("tooltipsplus-enabled", true);
        showTemperature = Core.settings.getBool("tooltipsplus-temp", true);
        showItemFlags = Core.settings.getBool("tooltipsplus-itemflags", true);
        debug = Core.settings.getBool("tooltipsplus-debug", false);
    }

    void saveSettings() {
        Core.settings.put("tooltipsplus-enabled", enabled);
        Core.settings.put("tooltipsplus-temp", showTemperature);
        Core.settings.put("tooltipsplus-itemflags", showItemFlags);
        Core.settings.put("tooltipsplus-debug", debug);
        Core.settings.forceSave();
    }

    void injectTooltips() {
        if (!enabled) {
            if (debug) Log.info("Tooltips+ disabled by settings, skipping injection.");
            return;
        }

        int blocks = 0, units = 0, items = 0, liquids = 0;

        // Blocks
        for (Block b : Vars.content.blocks()) {
            try {
                if (enhanceBlock(b)) blocks++;
            } catch (Throwable t) {
                if (debug) Log.err("Tooltips+: error enhancing block @", b.name, t);
            }
        }

        // Units
        for (UnitType u : Vars.content.units()) {
            try {
                if (enhanceUnit(u)) units++;
            } catch (Throwable t) {
                if (debug) Log.err("Tooltips+: error enhancing unit @", u.name, t);
            }
        }

        // Items
        for (Item i : Vars.content.items()) {
            try {
                if (enhanceItem(i)) items++;
            } catch (Throwable t) {
                if (debug) Log.err("Tooltips+: error enhancing item @", i.name, t);
            }
        }

        // Liquids
        for (Liquid l : Vars.content.liquids()) {
            try {
                if (enhanceLiquid(l)) liquids++;
            } catch (Throwable t) {
                if (debug) Log.err("Tooltips+: error enhancing liquid @", l.name, t);
            }
        }

        Log.info("Tooltips+ enhanced: @ blocks, @ units, @ items, @ liquids", blocks, units, items, liquids);
    }

    boolean enhanceBlock(Block block) {
        boolean changed = false;
        try {
            // health
            if (block.health > 0) {
                block.stats.add(Stat.health, (int) block.health);
                if (debug) Log.info("Tooltips+: added health to @ = @", block.name, (int) block.health);
                changed = true;
            }

            // item capacity
            if (block.hasItems() && block.itemCapacity > 0) {
                block.stats.add(Stat.itemCapacity, (int) block.itemCapacity);
                if (debug) Log.info("Tooltips+: added itemCapacity to @ = @", block.name, block.itemCapacity);
                changed = true;
            }

            // liquid capacity
            if (block.hasLiquids() && block.liquidCapacity > 0) {
                block.stats.add(Stat.liquidCapacity, block.liquidCapacity, StatUnit.liquidUnits);
                if (debug) Log.info("Tooltips+: added liquidCapacity to @ = @", block.name, Strings.autoFixed(block.liquidCapacity, 1));
                changed = true;
            }

            // power generator
            if (block instanceof PowerGenerator gen && gen.powerProduction > 0f) {
                float perSec = gen.powerProduction * 60f;
                block.stats.add(Stat.basePowerGeneration, perSec, StatUnit.powerSecond);
                if (debug) Log.info("Tooltips+: added power to @ = @/s", block.name, Strings.autoFixed(perSec, 1));
                changed = true;
            }

            // size (present as custom text so we don't rely on internal Stat typing)
            if (block.size > 1) {
                block.stats.add(Stat.input, "[lightgray]Size:[] " + block.size + "x" + block.size);
                if (debug) Log.info("Tooltips+: added size to @ = @x@", block.name, block.size, block.size);
                changed = true;
            }

            // approximate cost
            if (block.requirements != null && block.requirements.length > 0) {
                int total = 0;
                for (var req : block.requirements) total += req.amount;
                if (total > 50) {
                    block.stats.add(Stat.buildCost, total);
                    if (debug) Log.info("Tooltips+: added buildCost to @ = @", block.name, total);
                    changed = true;
                }
            }

        } catch (Throwable t) {
            if (debug) Log.err("Tooltips+: exception while enhancing block @", block.name, t);
            return false;
        }
        return changed;
    }

    boolean enhanceUnit(UnitType unit) {
        boolean changed = false;
        try {
            // health
            unit.stats.add(Stat.health, (int) unit.health);
            if (debug) Log.info("Tooltips+: unit health @ = @", unit.name, (int) unit.health);
            changed = true;

            // speed
            unit.stats.add(Stat.speed, unit.speed, StatUnit.tilesSecond);
            if (debug) Log.info("Tooltips+: unit speed @ = @", unit.name, Strings.autoFixed(unit.speed, 2));
            changed = true;

            // armor
            unit.stats.add(Stat.armor, (int) unit.armor);
            if (debug) Log.info("Tooltips+: unit armor @ = @", unit.name, (int) unit.armor);
            changed = true;

            // flying / special
            if (unit.flying) {
                unit.stats.add(Stat.input, "[accent]Flying[]");
                changed = true;
            }

            if (unit.mineSpeed > 0) {
                unit.stats.add(Stat.mineSpeed, unit.mineSpeed, StatUnit.perSecond);
                changed = true;
            }

            if (unit.buildSpeed > 0) {
                unit.stats.add(Stat.buildSpeed, unit.buildSpeed);
                changed = true;
            }

        } catch (Throwable t) {
            if (debug) Log.err("Tooltips+: exception while enhancing unit @", unit.name, t);
            return false;
        }
        return changed;
    }

    boolean enhanceItem(Item item) {
        boolean changed = false;
        try {
            if (item.hardness > 0 && showItemFlags) {
                // custom textual line via Stat.input (safe across versions)
                item.stats.add(Stat.input, "[lightgray]Hardness:[] " + Strings.autoFixed(item.hardness, 1));
                changed = true;
            }

            if (item.flammability > 0.1f && showItemFlags) {
                // flammability numeric stat (some versions support Stat.flammability, but using input keeps compatibility)
                item.stats.add(Stat.input, "[orange]Flammable[] " + Strings.autoFixed(item.flammability, 2));
                changed = true;
            }

            if (item.explosiveness > 0.1f && showItemFlags) {
                item.stats.add(Stat.input, "[scarlet]Explosive[] " + Strings.autoFixed(item.explosiveness, 2));
                changed = true;
            }

            if (item.radioactivity > 0.1f && showItemFlags) {
                item.stats.add(Stat.input, "[green]Radioactive[] " + Strings.autoFixed(item.radioactivity, 2));
                changed = true;
            }
        } catch (Throwable t) {
            if (debug) Log.err("Tooltips+: exception while enhancing item @", item.name, t);
            return false;
        }
        return changed;
    }

    boolean enhanceLiquid(Liquid liquid) {
        boolean changed = false;
        try {
            if (showTemperature) {
                liquid.stats.add(Stat.input, "[lightgray]Temp:[] " + Strings.autoFixed(liquid.temperature, 2));
                changed = true;
            }

            if (liquid.flammability > 0.1f) {
                liquid.stats.add(Stat.input, "[orange]Flammable[] " + Strings.autoFixed(liquid.flammability, 2));
                changed = true;
            }

            if (liquid.explosiveness > 0.1f) {
                liquid.stats.add(Stat.input, "[scarlet]Explosive[] " + Strings.autoFixed(liquid.explosiveness, 2));
                changed = true;
            }
        } catch (Throwable t) {
            if (debug) Log.err("Tooltips+: exception while enhancing liquid @", liquid.name, t);
            return false;
        }
        return changed;
    }

    /** Add a settings category similar to your JS snippet. */
    void addSettings() {
        // mindustry.ui.Icon is available in many builds; if your environment differs swap with Core.atlas.find("icon-...").
        Vars.ui.settings.addCategory("Tooltips+", Icons.book, table -> {
            table.add("[accent]Tooltips+ Settings").pad(8f).row();

            table.checkPref("tooltipsplus-enabled", enabled, val -> {
                enabled = val;
                saveSettings();
                // re-inject if needed
                if (enabled) injectTooltips();
            }).row();

            table.checkPref("tooltipsplus-temp", showTemperature, val -> {
                showTemperature = val;
                saveSettings();
            }).row();

            table.checkPref("tooltipsplus-itemflags", showItemFlags, val -> {
                showItemFlags = val;
                saveSettings();
            }).row();

            table.checkPref("tooltipsplus-debug", debug, val -> {
                debug = val;
                saveSettings();
            }).row();

            table.image().height(2f).width(400f).color(Styles.outlineColor).pad(8f).row();

            table.button("Re-run tooltip injection", () -> {
                injectTooltips();
                Vars.ui.showInfoToast("[lime]Tooltips re-injected", 1.5f);
            }).size(260f, 50f).pad(6f).row();

            table.button("Open Tooltips+ info", () -> {
                BaseDialog d = new BaseDialog("Tooltips+");
                d.cont.add("Adds extra fields to tooltips (health, capacity, power, etc.).").pad(6f);
                d.addCloseButton();
                d.show();
            }).size(260f, 50f).pad(6f).row();
        });
    }
}