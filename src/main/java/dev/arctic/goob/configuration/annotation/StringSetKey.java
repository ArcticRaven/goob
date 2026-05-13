package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@code ObjectOpenHashSet<String>} field to a YAML sequence.
 * Duplicate values in the file are collapsed by set semantics during bind.
 *
 * <p>If {@link #defaultValues()} is non-empty, those values are written as the initial
 * sequence when the key is absent. If empty (the default), an empty YAML sequence
 * ({@code []}) is written so the key is still visible and documented in the file.
 *
 * <pre>{@code
 * @StringSetKey(path = "moderation.banned-words", defaultValues = {"example", "placeholder"})
 * private ObjectOpenHashSet<String> bannedWords;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface StringSetKey {
    String path();
    String[] defaultValues() default {};
}
