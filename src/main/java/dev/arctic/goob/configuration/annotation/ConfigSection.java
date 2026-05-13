package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Marks a plain Java field whose type is itself a POJO carrying {@code @*Key} annotations.
 * {@link dev.arctic.goob.configuration.ConfigBinder ConfigBinder} will recursively
 * bind and unbind the nested object, resolving all of its key paths relative to
 * {@link #path()} as a prefix.
 *
 * <p>The nested class does <em>not</em> need to extend
 * {@link dev.arctic.goob.configuration.PluginConfig PluginConfig} — it is a plain
 * object instantiated by the binder via its no-arg constructor.
 *
 * <pre>{@code
 * // Nested POJO
 * public class DatabaseSection {
 *     @StringKey(path = "host", defaultValue = "localhost")
 *     public String host;
 *
 *     @IntKey(path = "port", defaultValue = "3306")
 *     public int port;
 * }
 *
 * // Parent config
 * @ConfigFile("config.yml")
 * public class MyConfig extends PluginConfig {
 *
 *     @ConfigSection(path = "database")
 *     private DatabaseSection database;
 *
 *     public DatabaseSection getDatabase() { return database; }
 * }
 * }</pre>
 *
 * <p>The above resolves {@code host} → {@code "database.host"} and
 * {@code port} → {@code "database.port"} in the YAML file.
 *
 * <p>Nesting is supported to any depth.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface ConfigSection {
    /**
     * The dot-separated path prefix applied to all key paths declared inside
     * the nested POJO, e.g. {@code "database"} or {@code "modules.economy"}.
     */
    String path();
}
