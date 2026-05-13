package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds a {@code String} field to a dot-separated YAML path that holds a CSS-style
 * hex color code.
 *
 * <p>Accepted formats: {@code #RRGGBB} or {@code #RRGGBBAA} (case-insensitive).
 * The value is written to YAML as a double-quoted string so the leading {@code #}
 * character is not misinterpreted as a YAML comment.
 *
 * <pre>{@code
 * @Comment("Primary brand color.")
 * @HexKey(path = "colors.primary", defaultValue = "#FF5500")
 * private String primaryColor;
 * }</pre>
 *
 * <p>If the value in the file is absent or malformed the field falls back to
 * {@link #defaultValue()}. Malformed values are logged at {@code SEVERE} for
 * critical configs and {@code WARNING} for non-critical ones.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface HexKey {
    String path();

    /**
     * Default hex color used when the key is absent or the stored value fails
     * validation. Must be a valid {@code #RRGGBB} or {@code #RRGGBBAA} string.
     */
    String defaultValue() default "#FFFFFF";
}
