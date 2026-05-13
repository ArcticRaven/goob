package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Marks a config field as sensitive. Fields carrying this annotation are excluded
 * from any debug output, config-dump commands, or logging that Brew may produce.
 *
 * <p>The value is still read and written normally — this annotation only affects
 * visibility in diagnostic output.
 *
 * <pre>{@code
 * @Sensitive
 * @StringKey(path = "database.password", defaultValue = "changeme")
 * private String dbPassword;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Sensitive {
}
