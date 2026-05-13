package dev.arctic.goob;

import dev.arctic.goob.configuration.ConfigUtil;
import dev.arctic.goob.configuration.PluginConfig;
import dev.arctic.goob.configuration.annotation.Comment;
import dev.arctic.goob.configuration.annotation.ConfigFile;
import dev.arctic.goob.configuration.annotation.SectionMapKey;
import dev.arctic.goob.configuration.annotation.StringListKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.snakeyaml.engine.v2.nodes.MappingNode;

import java.nio.file.Path;
import java.util.logging.Logger;

@ConfigFile(value = "config.yml", critical = true)
public class MainConfig extends PluginConfig {

    @Comment("Blocks that drop themselves when mined with Silk Touch.")
    @StringListKey(path = "extra-silk.blocks", defaultValues = {})
    private ObjectArrayList<String> extraSilkBlocks;

    @Comment({"Drop a different item instead of the block itself.",
              "Format:  BLOCK_MATERIAL: DROP_MATERIAL"})
    @SectionMapKey(path = "extra-silk.overrides")
    private MappingNode rawOverrides; // section placeholder — parsed manually in postBind

    private Object2ObjectOpenHashMap<String, String> extraSilkOverrides = new Object2ObjectOpenHashMap<>();

    public MainConfig(Path dataFolder, Logger logger) {
        super(dataFolder, logger);
    }

    @Override
    protected void postBind(MappingNode root) {
        extraSilkOverrides.clear();
        for (var block : ConfigUtil.getKeys(root, "extra-silk.overrides")) {
            var drop = ConfigUtil.getString(root, "extra-silk.overrides." + block, null);
            if (drop != null && !drop.isBlank()) extraSilkOverrides.put(block, drop);
        }
    }

    /** All blocks that should drop themselves under Silk Touch (unless overridden). */
    public ObjectArrayList<String> getExtraSilkBlocks() {
        return extraSilkBlocks;
    }

    /** Per-block drop overrides. Key = block material name, value = drop material name. */
    public Object2ObjectOpenHashMap<String, String> getExtraSilkOverrides() {
        return extraSilkOverrides;
    }
}
