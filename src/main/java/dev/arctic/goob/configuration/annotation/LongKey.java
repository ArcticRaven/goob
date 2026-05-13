package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Binds a {@code long} field to a dot-separated YAML path. Use this over
 * {@link IntKey} when the value may exceed {@link Integer#MAX_VALUE}, such as
 * timestamps, economy balances, or large counters.
 *
 * <pre>{@code
 * @LongKey(path = "economy.max-balance", defaultValue = "1000000000")
 * private long maxBalance;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface LongKey {
    String path();
    String defaultValue() default "0";
}
