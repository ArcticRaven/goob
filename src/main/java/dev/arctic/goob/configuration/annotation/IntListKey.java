package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@code IntArrayList} field to a YAML sequence of integer scalars.
 * Non-integer entries in the sequence are silently skipped during bind.
 *
 * <p>If {@link #defaultValues()} is non-empty, those values are written as the initial
 * sequence when the key is absent. If empty (the default), an empty YAML sequence
 * ({@code []}) is written so the key is still visible and documented in the file.
 *
 * <p>Values in {@link #defaultValues()} must be valid integer literals — a bad value
 * is a programmer error and will throw at startup.
 *
 * <pre>{@code
 * @IntListKey(path = "shop.featured-item-ids", defaultValues = {"1001", "1002", "1003"})
 * private IntArrayList featuredItemIds;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface IntListKey {
    String path();
    String[] defaultValues() default {};
}
