package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Attaches one or more comment lines above a key when it is written to disk.
 * Each element in {@link #value()} becomes a separate {@code # …} line in the file.
 *
 * <p>Comments are written during the default-injection pass and are preserved on
 * subsequent saves (SnakeYAML Engine carries comment nodes through its AST).
 * They are silently ignored if the underlying YAML library does not support comment
 * writing.
 *
 * <p>Can be combined with any {@code @*Key} or {@link ConfigSection} annotation.
 *
 * <pre>{@code
 * @Comment({"The host address of the database server.",
 *           "Use 'localhost' for a local installation."})
 * @StringKey(path = "database.host", defaultValue = "localhost")
 * private String dbHost;
 * }</pre>
 *
 * Produces:
 * <pre>
 * database:
 *   # The host address of the database server.
 *   # Use 'localhost' for a local installation.
 *   host: localhost
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Comment {
    /** One element per comment line. Leading {@code #} is added automatically. */
    String[] value();
}
