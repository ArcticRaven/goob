package dev.arctic.goob;

import dev.arctic.goob.configuration.ConfigUtil;
import dev.arctic.goob.configuration.PluginConfig;
import dev.arctic.goob.configuration.annotation.Comment;
import dev.arctic.goob.configuration.annotation.ConfigFile;
import dev.arctic.goob.configuration.annotation.SectionMapKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.snakeyaml.engine.v2.nodes.MappingNode;

import java.nio.file.Path;
import java.util.logging.Logger;

@ConfigFile(value = "config.yml", critical = true)
public class MainConfig extends PluginConfig {

    /**
     * Resolved drop entry for a silk-touch block.
     *
     * @param drop  the material that drops — may differ from the mined block
     * @param qty   how many of that material to drop
     * @param model optional item model override for resource-pack custom blocks (nullable)
     */
    public record SilkDrop(Material drop, int qty, NamespacedKey model) {}

    @Comment({
        "Silk Touch drop overrides. Each key is a block material name.",
        "Leave the value blank to drop the block itself at qty 1 (shorthand).",
        "Or use a subsection for full control:",
        "  STONE:                         # drops itself, qty 1",
        "  COAL_ORE:",
        "    qty: 3                       # drops itself, qty 3",
        "  BUDDING_AMETHYST:",
        "    drop: AMETHYST_BLOCK",
        "    qty: 2",
        "    model: mypack:custom_block   # optional — applies item model to the drop"
    })
    @SectionMapKey(path = "extra-silk")
    private Object2ObjectOpenHashMap<String, MappingNode> extraSilkSection;

    private Object2ObjectOpenHashMap<Material, SilkDrop> extraSilkDrops;

    public MainConfig(Path dataFolder, Logger logger) {
        super(dataFolder, logger);
    }

    @Override
    protected void postBind(MappingNode root) {
        extraSilkDrops = new Object2ObjectOpenHashMap<>();

        for (var key : ConfigUtil.getKeys(root, "extra-silk")) {
            var block = Material.matchMaterial(key);
            if (block == null) {
                logger().warning("[Goob] Unknown block material '" + key + "' in extra-silk — skipping.");
                continue;
            }

            var section = extraSilkSection != null ? extraSilkSection.get(key) : null;

            if (section == null) {
                extraSilkDrops.put(block, new SilkDrop(block, 1, null));
                continue;
            }

            // drop material
            var dropName = ConfigUtil.getString(section, "drop", null);
            Material drop;
            if (dropName == null || dropName.isBlank()) {
                drop = block;
            } else {
                drop = Material.matchMaterial(dropName);
                if (drop == null) {
                    logger().warning("[Goob] Unknown drop material '" + dropName + "' for '" + key + "' — skipping.");
                    continue;
                }
            }

            // qty
            var qty = ConfigUtil.getInt(section, "qty", 1);
            if (qty < 1) {
                logger().warning("[Goob] qty for '" + key + "' is less than 1 — skipping.");
                continue;
            }

            // model (optional)
            var modelStr = ConfigUtil.getString(section, "model", null);
            NamespacedKey model = null;
            if (modelStr != null && !modelStr.isBlank()) {
                model = NamespacedKey.fromString(modelStr);
                if (model == null) {
                    logger().warning("[Goob] Invalid model key '" + modelStr + "' for '" + key + "' — ignoring model.");
                }
            }

            extraSilkDrops.put(block, new SilkDrop(drop, qty, model));
        }
    }

    // ── Runtime mutation ─────────────────────────────────────────────────────────

    /**
     * Adds or replaces a silk-touch entry at runtime and persists it to disk.
     *
     * @param block block material to intercept
     * @param drop  material to drop (pass {@code block} for drop-itself behaviour)
     * @param qty   drop quantity (must be >= 1)
     * @param model optional item model key, or null
     */
    public void addSilkEntry(Material block, Material drop, int qty, NamespacedKey model) {
        extraSilkDrops.put(block, new SilkDrop(drop, qty, model));

        var section = ConfigUtil.getOrCreateSection(root(), "extra-silk." + block.name());
        ConfigUtil.setString(section, "drop", drop.name());
        ConfigUtil.setInt(section, "qty", qty);
        if (model != null) {
            ConfigUtil.setString(section, "model", model.toString());
        } else {
            ConfigUtil.remove(section, "model");
        }
        save();
    }

    /**
     * Removes a silk-touch entry at runtime and persists the change to disk.
     *
     * @param block block material to remove
     * @return true if an entry existed and was removed, false if nothing was configured for this block
     */
    public boolean removeSilkEntry(Material block) {
        if (!extraSilkDrops.containsKey(block)) return false;
        extraSilkDrops.remove(block);
        ConfigUtil.remove(root(), "extra-silk." + block.name());
        save();
        return true;
    }

    // ── Getters ──────────────────────────────────────────────────────────────────

    /** Returns the resolved extra-silk drop map. Key = block mined, value = drop config. */
    public Object2ObjectOpenHashMap<Material, SilkDrop> getExtraSilkDrops() {
        return extraSilkDrops;
    }

    /** Convenience — returns the SilkDrop for a given block, or null if not configured. */
    public SilkDrop getDropFor(Material block) {
        return extraSilkDrops.get(block);
    }
}
