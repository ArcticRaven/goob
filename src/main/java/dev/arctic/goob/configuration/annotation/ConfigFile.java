package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Marks a {@link dev.arctic.goob.configuration.PluginConfig} subclass and declares
 * the YAML file it binds to. The value is used as both the output path (relative to the
 * plugin data folder) and the classpath resource name for seeding on first load.
 *
 * <h2>Criticality</h2>
 * <p>When {@link #critical()} is {@code true} (the default), any binding error that
 * cannot be recovered with a default value marks the config as invalid. The plugin should
 * check {@link dev.arctic.goob.configuration.PluginConfig#isValid()} in
 * {@code onEnable()} and abort if it returns {@code false}.
 *
 * <p>When {@link #critical()} is {@code false}, all errors are logged as warnings and
 * the config is always considered valid — fields fall back to their declared defaults
 * or Java zero-values. Use this for optional feature configs where degraded behaviour
 * is acceptable.
 *
 * <pre>{@code
 * // Plugin cannot function without this — disable on failure
 * @ConfigFile(value = "config.yml", critical = true)
 * public class MyConfig extends PluginConfig { ... }
 *
 * // Nice-to-have config — log and continue if broken
 * @ConfigFile(value = "messages.yml", critical = false)
 * public class MessagesConfig extends PluginConfig { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface ConfigFile {

    /** File name relative to the plugin data folder, e.g. {@code "config.yml"}. */
    String value();

    /**
     * Whether this config is critical to plugin operation.
     *
     * <ul>
     *   <li>{@code true} (default) — binding errors that cannot fall back to a default
     *       mark the config as invalid via
     *       {@link dev.arctic.goob.configuration.PluginConfig#isValid()}.</li>
     *   <li>{@code false} — all errors are logged as {@code WARNING} and the config is
     *       always considered valid; fields fall back to defaults silently.</li>
     * </ul>
     */
    boolean critical() default true;
}
