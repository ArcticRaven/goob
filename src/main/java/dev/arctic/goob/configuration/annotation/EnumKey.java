package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds an {@link Enum} field to a dot-separated YAML path. The YAML value is matched
 * case-insensitively against enum constant names. If the value is absent or unrecognised,
 * {@code defaultValue} is used.
 *
 * <pre>{@code
 * public enum LogLevel { INFO, WARN, ERROR }
 *
 * @EnumKey(path = "logging.level", type = LogLevel.class, defaultValue = "INFO")
 * private LogLevel logLevel;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface EnumKey {
    String path();
    /** The concrete enum class to resolve constants from. */
    Class<? extends Enum<?>> type();
    /** Name of the default constant (case-insensitive). Must be a valid constant of {@link #type()}. */
    String defaultValue();
}
