package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@code int} field to a dot-separated YAML path.
 *
 * <pre>{@code
 * @IntKey(path = "database.port", defaultValue = "3306")
 * private int dbPort;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface IntKey {
    String path();
    String defaultValue() default "0";
}
