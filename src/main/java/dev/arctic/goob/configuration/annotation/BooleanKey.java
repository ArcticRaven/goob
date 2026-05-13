package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds a {@code boolean} field to a dot-separated YAML path.
 *
 * <pre>{@code
 * @BooleanKey(path = "features.pvp", defaultValue = "true")
 * private boolean pvpEnabled;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface BooleanKey {
    String path();
    String defaultValue() default "false";
}
