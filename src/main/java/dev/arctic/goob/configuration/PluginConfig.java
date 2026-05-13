package dev.arctic.goob.configuration;

import dev.arctic.goob.configuration.annotation.ConfigFile;
import dev.arctic.goob.configuration.annotation.SectionMapKey;
import dev.arctic.goob.configuration.annotation.Sensitive;
import org.snakeyaml.engine.v2.nodes.MappingNode;

import java.io.IOException;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for annotation-driven plugin configs in Brew.
 *
 * <h2>Minimal usage</h2>
 * <pre>{@code
 * @ConfigFile("config.yml")          // critical = true by default
 * public class MyConfig extends PluginConfig {
 *
 *     @Comment("Enable verbose logging.")
 *     @BooleanKey(path = "logging.debug", defaultValue = "false")
 *     private boolean debug;
 *
 *     public MyConfig(Path dataFolder, Logger logger) {
 *         super(dataFolder, logger);
 *     }
 *
 *     public boolean isDebug() { return debug; }
 * }
 * }</pre>
 *
 * <h2>Checking validity in onEnable</h2>
 * <pre>{@code
 * config = new MyConfig(getDataFolder().toPath(), getLogger());
 * if (!config.isValid()) {
 *     getLogger().severe("Config failed to load — disabling plugin.");
 *     getServer().getPluginManager().disablePlugin(this);
 *     return;
 * }
 * }</pre>
 *
 * <h2>Non-critical config (optional features)</h2>
 * <pre>{@code
 * @ConfigFile(value = "messages.yml", critical = false)
 * public class MessagesConfig extends PluginConfig { ... }
 * // isValid() always returns true — errors are warnings only
 * }</pre>
 *
 * <h2>Load pipeline (runs on construction and every reload())</h2>
 * <ol>
 *   <li>Copy bundled resource YAML to disk if file doesn't exist.</li>
 *   <li>Parse YAML into a {@link MappingNode} tree via {@link ConfigUtil#load}.</li>
 *   <li>Inject missing default values from annotations via {@link ConfigBinder#injectDefaults}.</li>
 *   <li>Flush the updated tree back to disk (persists newly-added keys).</li>
 *   <li>Bind all annotated fields via {@link ConfigBinder#bind}, validating values and
 *       logging errors at SEVERE (critical) or WARNING (non-critical).</li>
 *   <li>Call {@link #postBind(MappingNode)} for any additional custom logic.</li>
 * </ol>
 */
public abstract class PluginConfig {

    private final Path    filePath;
    private final Logger  logger;
    private final boolean critical;

    /**
     * The live YAML node tree. Kept in memory so {@link #save()} can write mutated
     * field values back without a full re-parse.
     */
    private MappingNode root;

    /**
     * {@code true} if the last load completed without any unrecoverable errors.
     * Always {@code true} for non-critical configs.
     */
    private boolean valid = true;

    /**
     * Constructs and immediately loads the config.
     *
     * <p>If the concrete class is annotated with {@link ConfigFile}, its {@code value}
     * is resolved relative to {@code dataFolder} and its {@code critical} flag is read.
     * Without the annotation, {@code config.yml} / critical {@code true} are used.
     *
     * @param dataFolder plugin data folder, e.g. {@code getDataFolder().toPath()}
     * @param logger     plugin logger used for validation messages and error reporting
     */
    protected PluginConfig(Path dataFolder, Logger logger) {
        this.logger   = logger;
        var ann       = getClass().getAnnotation(ConfigFile.class);
        this.filePath = dataFolder.resolve(ann != null ? ann.value() : "config.yml");
        this.critical = ann == null || ann.critical();
        load();
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the last load completed without unrecoverable errors.
     *
     * <p>For critical configs ({@link ConfigFile#critical()} = {@code true}), this
     * returns {@code false} if any field contained an invalid value that could not be
     * resolved to a safe default. Check this in {@code onEnable()} and disable the
     * plugin if it returns {@code false}.
     *
     * <p>For non-critical configs, always returns {@code true}.
     */
    public final boolean isValid() {
        return valid;
    }

    /**
     * Re-reads the YAML file from disk and rebinds all annotated fields.
     * Updates {@link #isValid()} based on the result.
     * Safe to call from a reload command at any time.
     */
    public final void reload() {
        load();
    }

    /**
     * Writes all annotated scalar field values back into the YAML node tree and
     * flushes to disk. Call this after mutating fields at runtime to persist changes.
     *
     * <p>Collection and map fields are not written back — edit the YAML directly and
     * call {@link #reload()} for those.
     */
    public final void save() {
        try {
            ConfigBinder.unbind(this, root);
            ConfigUtil.save(root, filePath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[Brew] Failed to save config: " + filePath, e);
        }
    }

    /**
     * Returns a human-readable dump of all config field values. Fields annotated
     * with {@link Sensitive @Sensitive} are replaced with {@code "***"}.
     * Useful for debug commands.
     */
    public final String dump() {
        return getClass().getSimpleName() + " [valid=" + valid + "]:\n" + ConfigBinder.dump(this);
    }

    /** Returns the absolute path of the config file managed by this instance. */
    public final Path getFilePath() {
        return filePath;
    }

    /** Returns the logger passed at construction. Available to subclasses for use in {@link #postBind}. */
    protected final Logger logger() {
        return logger;
    }

    // ── Extension point ──────────────────────────────────────────────────────────

    /**
     * Called after all annotated fields have been bound from the YAML tree.
     * Override this when your config has dynamic or computed structure that
     * cannot be expressed with annotations alone — for example, iterating a
     * {@link SectionMapKey} map to build typed domain objects.
     *
     * <p>The default implementation does nothing.
     *
     * @param root the fully-loaded YAML root node, available for any additional
     *             {@link ConfigUtil} calls you need
     */
    protected void postBind(MappingNode root) {}

    // ── Internal load pipeline ───────────────────────────────────────────────────

    public void load() {
        try {
            root = ConfigUtil.load(filePath);   // empty mapping if file doesn't exist yet
            ConfigBinder.injectDefaults(this, root);
            ConfigUtil.save(root, filePath);    // writes generated file on first run, patches on subsequent
            var result = ConfigBinder.bind(this, root, logger, critical);
            valid = !critical || result == ConfigBinder.BindResult.OK;
            if (!valid) {
                logger.severe("[Brew] Config '%s' failed validation — plugin should be disabled."
                        .formatted(filePath.getFileName()));
            }
            postBind(root);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "[Brew] Failed to load config: " + filePath, e);
            if (critical) valid = false;
        }
    }
}
