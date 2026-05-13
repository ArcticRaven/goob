package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds a {@code String} field to a dot-separated YAML path.
 *
 * <pre>{@code
 * @StringKey(path = "database.host", defaultValue = "localhost")
 * private String dbHost;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface StringKey {
    String path();
    String defaultValue() default "";
}
