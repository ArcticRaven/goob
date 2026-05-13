package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@code Object2ObjectOpenHashMap<String, MappingNode>} field to a YAML mapping
 * whose values are themselves subsections. Use this for dynamic keyed blocks where the
 * set of keys is not known at compile time.
 *
 * <p>The raw {@code MappingNode} values can be read with {@code ConfigUtil} getters.
 *
 * <pre>{@code
 * # config.yml
 * worlds:
 *   world:
 *     pvp: true
 *     max-players: 50
 *   world_nether:
 *     pvp: false
 *     max-players: 20
 * }</pre>
 *
 * <pre>{@code
 * @SectionMapKey(path = "worlds")
 * private Object2ObjectOpenHashMap<String, MappingNode> worldSettings;
 *
 * // Usage:
 * var section = worldSettings.get("world");
 * boolean pvp = ConfigUtil.getBoolean(section, "pvp", false);
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface SectionMapKey {
    String path();
}
