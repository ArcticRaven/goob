package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds a {@code double} field to a dot-separated YAML path.
 *
 * <pre>{@code
 * @DoubleKey(path = "economy.tax-rate", defaultValue = "0.05")
 * private double taxRate;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface DoubleKey {
    String path();
    String defaultValue() default "0.0";
}
