package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@code ObjectArrayList<String>} field to a YAML sequence at the given path.
 *
 * <p>If {@link #defaultValues()} is non-empty, those values are written as the initial
 * sequence when the key is absent. If empty (the default), an empty YAML sequence
 * ({@code []}) is written so the key is still visible and documented in the file.
 *
 * <pre>{@code
 * @StringListKey(path = "server.disabled-worlds", defaultValues = {"world_nether", "world_the_end"})
 * private ObjectArrayList<String> disabledWorlds;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface StringListKey {
    String path();
    String[] defaultValues() default {};
}
