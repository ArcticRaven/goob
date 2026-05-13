package dev.arctic.goob.configuration.annotation;

import java.lang.annotation.*;

/**
 * Marks an annotated config field as optional. When the key is absent from the YAML
 * file, the field retains its Java default value ({@code null} for objects, {@code 0},
 * {@code false}, etc.) and <em>no</em> default entry is written to the file.
 *
 * <p>Without {@code @Optional}, missing keys are always injected with their
 * {@code defaultValue} and flushed to disk on first load.
 *
 * <p>Combine with any {@code @*Key} annotation. The {@code defaultValue} attribute
 * of the paired annotation is used only when the key <em>is</em> present but
 * unparseable; it is never written to disk when {@code @Optional} is present.
 *
 * <pre>{@code
 * @Optional
 * @StringKey(path = "integrations.discord-webhook")
 * private String discordWebhook;   // null when not configured
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Optional {
}
