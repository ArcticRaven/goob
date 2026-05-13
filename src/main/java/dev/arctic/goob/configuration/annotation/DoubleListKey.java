package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds a {@code DoubleArrayList} field to a YAML sequence of floating-point scalars.
 * Non-numeric entries in the sequence are silently skipped during bind.
 *
 * <p>If {@link #defaultValues()} is non-empty, those values are written as the initial
 * sequence when the key is absent. If empty (the default), an empty YAML sequence
 * ({@code []}) is written so the key is still visible and documented in the file.
 *
 * <p>Values in {@link #defaultValues()} must be valid double literals — a bad value
 * is a programmer error and will throw at startup.
 *
 * <pre>{@code
 * @DoubleListKey(path = "drops.chances", defaultValues = {"0.1", "0.25", "0.05"})
 * private DoubleArrayList dropChances;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface DoubleListKey {
    String path();
    String[] defaultValues() default {};
}
