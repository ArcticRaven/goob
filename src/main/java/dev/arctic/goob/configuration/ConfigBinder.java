package dev.arctic.goob.configuration;

import dev.arctic.goob.configuration.annotation.*;
import dev.arctic.goob.configuration.annotation.Optional;
import java.util.regex.Pattern;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Tag;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reflection-driven binder that maps annotated fields on a
 * {@link PluginConfig} subclass to and from a live {@link MappingNode} tree.
 *
 * <h2>Supported annotations</h2>
 * <table>
 *   <caption>Annotation to field type mapping</caption>
 *   <tr><th>Annotation</th><th>Field type</th></tr>
 *   <tr><td>{@link StringKey}</td>      <td>{@code String}</td></tr>
 *   <tr><td>{@link HexKey}</td>         <td>{@code String} (#RRGGBB / #RRGGBBAA)</td></tr>
 *   <tr><td>{@link IntKey}</td>         <td>{@code int}</td></tr>
 *   <tr><td>{@link LongKey}</td>        <td>{@code long}</td></tr>
 *   <tr><td>{@link DoubleKey}</td>      <td>{@code double}</td></tr>
 *   <tr><td>{@link BooleanKey}</td>     <td>{@code boolean}</td></tr>
 *   <tr><td>{@link EnumKey}</td>        <td>any {@code Enum}</td></tr>
 *   <tr><td>{@link StringListKey}</td>  <td>{@code ObjectArrayList<String>}</td></tr>
 *   <tr><td>{@link IntListKey}</td>     <td>{@code IntArrayList}</td></tr>
 *   <tr><td>{@link DoubleListKey}</td>  <td>{@code DoubleArrayList}</td></tr>
 *   <tr><td>{@link StringSetKey}</td>   <td>{@code ObjectOpenHashSet<String>}</td></tr>
 *   <tr><td>{@link SectionMapKey}</td>  <td>{@code Object2ObjectOpenHashMap<String, MappingNode>}</td></tr>
 *   <tr><td>{@link ConfigSection}</td>  <td>any plain POJO</td></tr>
 * </table>
 *
 * <h2>Modifier annotations</h2>
 * <ul>
 *   <li>{@link Optional}   — field keeps its Java default when the key is absent; no default is written.</li>
 *   <li>{@link Sensitive}  — field is excluded from any debug/dump output.</li>
 *   <li>{@link Comment}    — comment lines are attached above the key in the YAML file.</li>
 * </ul>
 *
 * <h2>Error handling</h2>
 * <p>Errors during binding are handled based on the {@link ConfigFile#critical()} flag:
 * <ul>
 *   <li><b>Critical configs</b> — errors are logged at {@code SEVERE} and the bind result
 *       is marked invalid. {@link PluginConfig#isValid()} will return {@code false}.</li>
 *   <li><b>Non-critical configs</b> — errors are logged at {@code WARNING} and the field
 *       falls back to its declared default. The config is always considered valid.</li>
 * </ul>
 *
 * <p>This class is internal to Brew. Plugin authors interact exclusively through
 * {@link PluginConfig} and the annotation set.
 */
public final class ConfigBinder {

    private ConfigBinder() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ── Bind result ──────────────────────────────────────────────────────────────

    /**
     * Returned by {@link #bind} to indicate whether binding completed cleanly.
     * {@link PluginConfig} uses this to set its {@code valid} state.
     */
    public enum BindResult { OK, FAILED }

    // ── Read: node → fields ──────────────────────────────────────────────────────

    /**
     * Populates all annotated fields on {@code target} from {@code root}.
     *
     * @param target     the object whose fields are to be populated
     * @param root       the YAML root (or sub-section) node to read from
     * @param pathPrefix dot-separated prefix applied to every path in this scope
     * @param logger     plugin logger for validation warnings and errors
     * @param critical   if {@code true}, field errors are logged at SEVERE and
     *                   {@link BindResult#FAILED} is returned; otherwise logged at WARNING
     *                   and {@link BindResult#OK} is always returned
     * @return {@link BindResult#OK} if all fields bound cleanly or the config is
     *         non-critical, {@link BindResult#FAILED} if any critical field error occurred
     */
    public static BindResult bind(Object target, MappingNode root, String pathPrefix,
                                  Logger logger, boolean critical) {
        var failed = false;
        for (var field : collectFields(target.getClass())) {
            field.setAccessible(true);
            try {
                var ok = bindField(target, field, root, pathPrefix, logger, critical);
                if (!ok && critical) failed = true;
            } catch (ReflectiveOperationException e) {
                logError(logger, critical,
                        "ConfigBinder: cannot access field '%s' on %s: %s"
                                .formatted(field.getName(), target.getClass().getSimpleName(),
                                        e.getMessage()));
                if (critical) failed = true;
            }
        }
        return failed ? BindResult.FAILED : BindResult.OK;
    }

    /** Convenience overload for top-level binding (no prefix). */
    public static BindResult bind(Object target, MappingNode root, Logger logger, boolean critical) {
        return bind(target, root, "", logger, critical);
    }

    /**
     * Binds a single field. Returns {@code false} if a non-recoverable error occurred
     * that should mark a critical config as invalid.
     */
    private static boolean bindField(Object target, Field field, MappingNode root,
                                     String prefix, Logger logger, boolean critical)
            throws ReflectiveOperationException {

        // ── Nested section ───────────────────────────────────────────────────────
        if (field.isAnnotationPresent(ConfigSection.class)) {
            var ann         = field.getAnnotation(ConfigSection.class);
            var sectionPath = qualify(prefix, ann.path());
            var instance    = field.getType().getDeclaredConstructor().newInstance();
            var result      = bind(instance, root, sectionPath, logger, critical);
            field.set(target, instance);
            return result == BindResult.OK;
        }

        // ── Scalar and collection keys ───────────────────────────────────────────
        var qualifiedPath = qualifiedPath(field, prefix);
        if (qualifiedPath == null) return true; // unannotated — not an error

        return switch (field) {
            case Field f when f.isAnnotationPresent(StringKey.class) -> {
                f.set(target, ConfigUtil.getString(root, qualifiedPath,
                        f.getAnnotation(StringKey.class).defaultValue()));
                yield true;
            }
            case Field f when f.isAnnotationPresent(HexKey.class) -> {
                var ann    = f.getAnnotation(HexKey.class);
                var raw    = ConfigUtil.getString(root, qualifiedPath, null);
                var parsed = parseHexValue(raw, qualifiedPath, ann.defaultValue(), f, logger, critical);
                f.set(target, parsed.value());
                yield parsed.ok();
            }
            case Field f when f.isAnnotationPresent(IntKey.class) -> {
                var ann     = f.getAnnotation(IntKey.class);
                var def     = parseIntSafe(ann.defaultValue(), f, logger, critical);
                var raw     = ConfigUtil.getString(root, qualifiedPath, null);
                var parsed  = parseIntValue(raw, qualifiedPath, def, f, logger, critical);
                f.setInt(target, parsed.value());
                yield parsed.ok();
            }
            case Field f when f.isAnnotationPresent(LongKey.class) -> {
                var ann    = f.getAnnotation(LongKey.class);
                var def    = parseLongSafe(ann.defaultValue(), f, logger, critical);
                var raw    = ConfigUtil.getString(root, qualifiedPath, null);
                var parsed = parseLongValue(raw, qualifiedPath, def, f, logger, critical);
                f.setLong(target, parsed.value());
                yield parsed.ok();
            }
            case Field f when f.isAnnotationPresent(DoubleKey.class) -> {
                var ann    = f.getAnnotation(DoubleKey.class);
                var def    = parseDoubleSafe(ann.defaultValue(), f, logger, critical);
                var raw    = ConfigUtil.getString(root, qualifiedPath, null);
                var parsed = parseDoubleValue(raw, qualifiedPath, def, f, logger, critical);
                f.setDouble(target, parsed.value());
                yield parsed.ok();
            }
            case Field f when f.isAnnotationPresent(BooleanKey.class) -> {
                var raw = ConfigUtil.getString(root, qualifiedPath, null);
                if (raw != null && !raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                    logError(logger, critical,
                            "ConfigBinder: '%s' has invalid boolean value '%s' — expected true/false, using default '%s'"
                                    .formatted(qualifiedPath, raw,
                                            f.getAnnotation(BooleanKey.class).defaultValue()));
                    f.setBoolean(target, Boolean.parseBoolean(f.getAnnotation(BooleanKey.class).defaultValue()));
                    yield !critical; // only a failure for critical configs
                }
                f.setBoolean(target, ConfigUtil.getBoolean(root, qualifiedPath,
                        Boolean.parseBoolean(f.getAnnotation(BooleanKey.class).defaultValue())));
                yield true;
            }
            case Field f when f.isAnnotationPresent(EnumKey.class) -> {
                var ann    = f.getAnnotation(EnumKey.class);
                var result = bindEnumSafe(root, qualifiedPath, ann.type(), ann.defaultValue(),
                        f, logger, critical);
                f.set(target, result.value());
                yield result.ok();
            }
            case Field f when f.isAnnotationPresent(StringListKey.class) -> {
                f.set(target, ConfigUtil.getStringList(root, qualifiedPath));
                yield true;
            }
            case Field f when f.isAnnotationPresent(IntListKey.class) -> {
                f.set(target, ConfigUtil.getIntList(root, qualifiedPath));
                yield true;
            }
            case Field f when f.isAnnotationPresent(DoubleListKey.class) -> {
                f.set(target, ConfigUtil.getDoubleList(root, qualifiedPath));
                yield true;
            }
            case Field f when f.isAnnotationPresent(StringSetKey.class) -> {
                f.set(target, ConfigUtil.getStringSet(root, qualifiedPath));
                yield true;
            }
            case Field f when f.isAnnotationPresent(SectionMapKey.class) -> {
                f.set(target, ConfigUtil.getSectionMap(root, qualifiedPath));
                yield true;
            }
            default -> true;
        };
    }

    // ── Write: fields → node ─────────────────────────────────────────────────────

    /**
     * Writes all annotated scalar field values from {@code source} back into {@code root}.
     * Call before {@link ConfigUtil#save} to persist runtime mutations.
     *
     * <p>Collection fields ({@link StringListKey}, {@link IntListKey}, etc.) and
     * {@link SectionMapKey} fields are intentionally skipped — edit the YAML directly
     * and call {@code reload()} for those.
     *
     * @param source     the object to read values from
     * @param root       the YAML node tree to write into
     * @param pathPrefix dot-separated prefix for this scope
     */
    public static void unbind(Object source, MappingNode root, String pathPrefix) {
        for (var field : collectFields(source.getClass())) {
            field.setAccessible(true);
            try {
                unbindField(source, field, root, pathPrefix);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "ConfigBinder: cannot unbind field '%s' on %s"
                                .formatted(field.getName(), source.getClass().getSimpleName()), e);
            }
        }
    }

    /** Convenience overload for top-level unbinding (no prefix). */
    public static void unbind(Object source, MappingNode root) {
        unbind(source, root, "");
    }

    private static void unbindField(Object source, Field field, MappingNode root, String prefix)
            throws ReflectiveOperationException {

        if (field.isAnnotationPresent(ConfigSection.class)) {
            var ann    = field.getAnnotation(ConfigSection.class);
            var nested = field.get(source);
            if (nested != null) unbind(nested, root, qualify(prefix, ann.path()));
            return;
        }

        var qualifiedPath = qualifiedPath(field, prefix);
        if (qualifiedPath == null) return;

        var parts       = qualifiedPath.split("\\.", -1);
        var leafKey     = parts[parts.length - 1];
        var sectionPath = qualifiedPath.contains(".")
                ? qualifiedPath.substring(0, qualifiedPath.lastIndexOf('.'))
                : "";
        var section = sectionPath.isEmpty()
                ? root
                : ConfigUtil.getOrCreateSection(root, sectionPath);

        switch (field) {
            case Field f when f.isAnnotationPresent(StringKey.class) ->
                    ConfigUtil.setString(section, leafKey, (String) f.get(source));
            case Field f when f.isAnnotationPresent(HexKey.class) ->
                    ConfigUtil.setStringQuoted(section, leafKey, (String) f.get(source));
            case Field f when f.isAnnotationPresent(IntKey.class) ->
                    ConfigUtil.setInt(section, leafKey, f.getInt(source));
            case Field f when f.isAnnotationPresent(LongKey.class) ->
                    ConfigUtil.setLong(section, leafKey, f.getLong(source));
            case Field f when f.isAnnotationPresent(DoubleKey.class) ->
                    ConfigUtil.setDouble(section, leafKey, f.getDouble(source));
            case Field f when f.isAnnotationPresent(BooleanKey.class) ->
                    ConfigUtil.setBoolean(section, leafKey, f.getBoolean(source));
            case Field f when f.isAnnotationPresent(EnumKey.class) ->
                    ConfigUtil.setEnum(section, leafKey, (Enum<?>) f.get(source));
            default -> { /* lists / sets / maps: read-only at runtime */ }
        }
    }

    // ── Default Injection ────────────────────────────────────────────────────────

    /**
     * Walks all annotated fields and writes their {@code defaultValue} into {@code root}
     * for any keys that are currently absent. Fields marked {@link Optional} are skipped.
     * Called during initial load so every declared key appears in the file after first run.
     *
     * @param target     the config object (used only for class reflection)
     * @param root       the YAML node tree to patch
     * @param pathPrefix dot-separated prefix for this scope
     */
    public static void injectDefaults(Object target, MappingNode root, String pathPrefix) {
        for (var field : collectFields(target.getClass())) {
            field.setAccessible(true);
            try {
                injectDefault(target, field, root, pathPrefix);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(
                        "ConfigBinder: cannot inject default for field '%s' on %s"
                                .formatted(field.getName(), target.getClass().getSimpleName()), e);
            }
        }
    }

    /** Convenience overload for top-level injection (no prefix). */
    public static void injectDefaults(Object target, MappingNode root) {
        injectDefaults(target, root, "");
    }

    private static void injectDefault(Object target, Field field, MappingNode root, String prefix)
            throws ReflectiveOperationException {

        if (field.isAnnotationPresent(ConfigSection.class)) {
            var ann      = field.getAnnotation(ConfigSection.class);
            var instance = field.getType().getDeclaredConstructor().newInstance();
            injectDefaults(instance, root, qualify(prefix, ann.path()));
            return;
        }

        if (field.isAnnotationPresent(Optional.class)) return;

        var qualifiedPath = qualifiedPath(field, prefix);
        if (qualifiedPath == null) return;

        if (ConfigUtil.contains(root, qualifiedPath)) return;

        var parts       = qualifiedPath.split("\\.", -1);
        var leafKey     = parts[parts.length - 1];
        var sectionPath = qualifiedPath.contains(".")
                ? qualifiedPath.substring(0, qualifiedPath.lastIndexOf('.'))
                : "";
        var section = sectionPath.isEmpty()
                ? root
                : ConfigUtil.getOrCreateSection(root, sectionPath);

        switch (field) {
            case Field f when f.isAnnotationPresent(StringKey.class) ->
                    ConfigUtil.setString(section, leafKey, f.getAnnotation(StringKey.class).defaultValue());
            case Field f when f.isAnnotationPresent(HexKey.class) -> {
                var def = f.getAnnotation(HexKey.class).defaultValue();
                validateHexDefault(def, f.getName()); // programmer error — always throw
                ConfigUtil.setStringQuoted(section, leafKey, def);
            }
            case Field f when f.isAnnotationPresent(IntKey.class) ->
                    ConfigUtil.setInt(section, leafKey, parseIntDefault(f.getAnnotation(IntKey.class).defaultValue(), f.getName()));
            case Field f when f.isAnnotationPresent(LongKey.class) ->
                    ConfigUtil.setLong(section, leafKey, parseLongDefault(f.getAnnotation(LongKey.class).defaultValue(), f.getName()));
            case Field f when f.isAnnotationPresent(DoubleKey.class) ->
                    ConfigUtil.setDouble(section, leafKey, parseDoubleDefault(f.getAnnotation(DoubleKey.class).defaultValue(), f.getName()));
            case Field f when f.isAnnotationPresent(BooleanKey.class) ->
                    ConfigUtil.setBoolean(section, leafKey, Boolean.parseBoolean(f.getAnnotation(BooleanKey.class).defaultValue()));
            case Field f when f.isAnnotationPresent(EnumKey.class) ->
                    ConfigUtil.setString(section, leafKey, f.getAnnotation(EnumKey.class).defaultValue());
            case Field f when f.isAnnotationPresent(StringListKey.class) ->
                    ConfigUtil.setSequence(section, leafKey, f.getAnnotation(StringListKey.class).defaultValues(), Tag.STR);
            case Field f when f.isAnnotationPresent(IntListKey.class) -> {
                var vals = f.getAnnotation(IntListKey.class).defaultValues();
                for (var v : vals) parseIntDefault(v, f.getName()); // validate at startup
                ConfigUtil.setSequence(section, leafKey, vals, Tag.INT);
            }
            case Field f when f.isAnnotationPresent(DoubleListKey.class) -> {
                var vals = f.getAnnotation(DoubleListKey.class).defaultValues();
                for (var v : vals) parseDoubleDefault(v, f.getName()); // validate at startup
                ConfigUtil.setSequence(section, leafKey, vals, Tag.FLOAT);
            }
            case Field f when f.isAnnotationPresent(StringSetKey.class) ->
                    ConfigUtil.setSequence(section, leafKey, f.getAnnotation(StringSetKey.class).defaultValues(), Tag.STR);
            case Field f when f.isAnnotationPresent(SectionMapKey.class) -> {
                // Dynamic keyed blocks cannot be defaulted from an annotation.
                // Write an empty mapping so the key is present and documented in the file.
                ConfigUtil.createSection(section, leafKey);
            }
            default -> {}
        }

        if (field.isAnnotationPresent(Comment.class)) {
            ConfigUtil.setComment(root, qualifiedPath, field.getAnnotation(Comment.class).value());
        }
    }

    // ── Debug / Dump ─────────────────────────────────────────────────────────────

    /**
     * Produces a human-readable dump of all config field values for logging.
     * Fields marked {@link Sensitive} are replaced with {@code "***"}.
     *
     * @param target the config object to dump
     * @return multi-line string with one {@code key = value} entry per field
     */
    public static String dump(Object target) {
        var sb     = new StringBuilder();
        var fields = collectFields(target.getClass());
        for (var field : fields) {
            field.setAccessible(true);
            var name      = field.getName();
            var sensitive = field.isAnnotationPresent(Sensitive.class);
            try {
                var value = sensitive ? "***" : field.get(target);
                sb.append("  ").append(name).append(" = ").append(value).append('\n');
            } catch (IllegalAccessException ignored) {
                sb.append("  ").append(name).append(" = <inaccessible>\n");
            }
        }
        return sb.toString();
    }

    // ── Validated parse helpers ──────────────────────────────────────────────────

    /**
     * Sealed result carrier — avoids allocating an exception for normal bad-value paths.
     * {@link #ok()} tells the caller whether to mark the config failed.
     */
    private sealed interface ParseResult<T> permits ParseResult.Good, ParseResult.Bad {
        T value();
        boolean ok();

        record Good<T>(T value) implements ParseResult<T> { public boolean ok() { return true; } }
        record Bad<T>(T value)  implements ParseResult<T> { public boolean ok() { return false; } }

        static <T> ParseResult<T> good(T v) { return new Good<>(v); }
        static <T> ParseResult<T> bad(T v)  { return new Bad<>(v); }
    }

    private static ParseResult<Integer> parseIntValue(
            String raw, String path, int def, Field field, Logger logger, boolean critical) {
        if (raw == null) return ParseResult.good(def);
        try {
            return ParseResult.good(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            logError(logger, critical,
                    "ConfigBinder: '%s' has invalid int value '%s' — using default %d"
                            .formatted(path, raw, def));
            return critical ? ParseResult.bad(def) : ParseResult.good(def);
        }
    }

    private static ParseResult<Long> parseLongValue(
            String raw, String path, long def, Field field, Logger logger, boolean critical) {
        if (raw == null) return ParseResult.good(def);
        try {
            return ParseResult.good(Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            logError(logger, critical,
                    "ConfigBinder: '%s' has invalid long value '%s' — using default %d"
                            .formatted(path, raw, def));
            return critical ? ParseResult.bad(def) : ParseResult.good(def);
        }
    }

    private static ParseResult<Double> parseDoubleValue(
            String raw, String path, double def, Field field, Logger logger, boolean critical) {
        if (raw == null) return ParseResult.good(def);
        try {
            return ParseResult.good(Double.parseDouble(raw.trim()));
        } catch (NumberFormatException e) {
            logError(logger, critical,
                    "ConfigBinder: '%s' has invalid double value '%s' — using default %f"
                            .formatted(path, raw, def));
            return critical ? ParseResult.bad(def) : ParseResult.good(def);
        }
    }

    private static <E extends Enum<E>> ParseResult<E> bindEnumSafe(
            MappingNode root, String path, Class<? extends Enum<?>> rawType,
            String defaultName, Field field, Logger logger, boolean critical) {

        @SuppressWarnings("unchecked")
        var type = (Class<E>) rawType;

        // Validate the declared default first — this is a programmer error, always throw
        E fallback = null;
        for (var constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(defaultName)) { fallback = constant; break; }
        }
        if (fallback == null) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @EnumKey defaultValue \"%s\" on field '%s' is not a constant of %s"
                            .formatted(defaultName, field.getName(), type.getSimpleName()));
        }

        // Now read and validate the YAML value
        var raw = ConfigUtil.getString(root, path, null);
        if (raw == null) return ParseResult.good(fallback);

        for (var constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(raw.trim())) return ParseResult.good(constant);
        }

        logError(logger, critical,
                "ConfigBinder: '%s' has unrecognised %s value '%s' — valid values: %s, using default '%s'"
                        .formatted(path, type.getSimpleName(),
                                raw,
                                java.util.Arrays.stream(type.getEnumConstants())
                                        .map(Enum::name)
                                        .collect(java.util.stream.Collectors.joining(", ")),
                                defaultName));
        return critical ? ParseResult.bad(fallback) : ParseResult.good(fallback);
    }

    // ── Default parse helpers (annotation values — programmer errors, always throw) ──

    private static int parseIntSafe(String value, Field field, Logger logger, boolean critical) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @IntKey defaultValue \"%s\" on field '%s' is not a valid int"
                            .formatted(value, field.getName()));
        }
    }

    private static long parseLongSafe(String value, Field field, Logger logger, boolean critical) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @LongKey defaultValue \"%s\" on field '%s' is not a valid long"
                            .formatted(value, field.getName()));
        }
    }

    private static double parseDoubleSafe(String value, Field field, Logger logger, boolean critical) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @DoubleKey defaultValue \"%s\" on field '%s' is not a valid double"
                            .formatted(value, field.getName()));
        }
    }

    // These remain for use in injectDefaults where we have no logger context
    private static int parseIntDefault(String value, String fieldName) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @IntKey defaultValue \"%s\" on field '%s' is not a valid int"
                            .formatted(value, fieldName));
        }
    }

    private static long parseLongDefault(String value, String fieldName) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @LongKey defaultValue \"%s\" on field '%s' is not a valid long"
                            .formatted(value, fieldName));
        }
    }

    private static double parseDoubleDefault(String value, String fieldName) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @DoubleKey defaultValue \"%s\" on field '%s' is not a valid double"
                            .formatted(value, fieldName));
        }
    }

    // ── Hex color helpers ────────────────────────────────────────────────────────

    /** Matches {@code #RRGGBB} and {@code #RRGGBBAA} (case-insensitive). */
    private static final Pattern HEX_PATTERN =
            Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");

    private static ParseResult<String> parseHexValue(
            String raw, String path, String def, Field field, Logger logger, boolean critical) {
        if (raw == null) return ParseResult.good(def);
        var trimmed = raw.trim();
        if (HEX_PATTERN.matcher(trimmed).matches()) {
            return ParseResult.good(trimmed);
        }
        logError(logger, critical,
                "ConfigBinder: '%s' has invalid hex color value '%s' — expected #RRGGBB or #RRGGBBAA, using default '%s'"
                        .formatted(path, raw, def));
        return critical ? ParseResult.bad(def) : ParseResult.good(def);
    }

    /** Validates a {@link HexKey} {@code defaultValue} at startup — programmer error, always throws. */
    private static void validateHexDefault(String value, String fieldName) {
        if (!HEX_PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                    "ConfigBinder: @HexKey defaultValue \"%s\" on field '%s' is not a valid hex color (#RRGGBB or #RRGGBBAA)"
                            .formatted(value, fieldName));
        }
    }

    // ── Logging ──────────────────────────────────────────────────────────────────

    /**
     * Logs a message at {@code SEVERE} for critical configs, {@code WARNING} for skippable ones.
     */
    private static void logError(Logger logger, boolean critical, String message) {
        logger.log(critical ? Level.SEVERE : Level.WARNING, "[Brew] " + message);
    }

    // ── Field collection helpers ─────────────────────────────────────────────────

    private static ObjectArrayList<Field> collectFields(Class<?> clazz) {
        var fields  = new ObjectArrayList<Field>();
        var current = clazz;
        while (current != null && current != PluginConfig.class && current != Object.class) {
            for (var f : current.getDeclaredFields()) {
                if (isConfigField(f)) fields.add(f);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static boolean isConfigField(Field f) {
        return f.isAnnotationPresent(StringKey.class)
                || f.isAnnotationPresent(HexKey.class)
                || f.isAnnotationPresent(IntKey.class)
                || f.isAnnotationPresent(LongKey.class)
                || f.isAnnotationPresent(DoubleKey.class)
                || f.isAnnotationPresent(BooleanKey.class)
                || f.isAnnotationPresent(EnumKey.class)
                || f.isAnnotationPresent(StringListKey.class)
                || f.isAnnotationPresent(IntListKey.class)
                || f.isAnnotationPresent(DoubleListKey.class)
                || f.isAnnotationPresent(StringSetKey.class)
                || f.isAnnotationPresent(SectionMapKey.class)
                || f.isAnnotationPresent(ConfigSection.class);
    }

    private static String qualifiedPath(Field field, String prefix) {
        var raw = switch (field) {
            case Field f when f.isAnnotationPresent(StringKey.class)     -> f.getAnnotation(StringKey.class).path();
            case Field f when f.isAnnotationPresent(HexKey.class)        -> f.getAnnotation(HexKey.class).path();
            case Field f when f.isAnnotationPresent(IntKey.class)        -> f.getAnnotation(IntKey.class).path();
            case Field f when f.isAnnotationPresent(LongKey.class)       -> f.getAnnotation(LongKey.class).path();
            case Field f when f.isAnnotationPresent(DoubleKey.class)     -> f.getAnnotation(DoubleKey.class).path();
            case Field f when f.isAnnotationPresent(BooleanKey.class)    -> f.getAnnotation(BooleanKey.class).path();
            case Field f when f.isAnnotationPresent(EnumKey.class)       -> f.getAnnotation(EnumKey.class).path();
            case Field f when f.isAnnotationPresent(StringListKey.class) -> f.getAnnotation(StringListKey.class).path();
            case Field f when f.isAnnotationPresent(IntListKey.class)    -> f.getAnnotation(IntListKey.class).path();
            case Field f when f.isAnnotationPresent(DoubleListKey.class) -> f.getAnnotation(DoubleListKey.class).path();
            case Field f when f.isAnnotationPresent(StringSetKey.class)  -> f.getAnnotation(StringSetKey.class).path();
            case Field f when f.isAnnotationPresent(SectionMapKey.class) -> f.getAnnotation(SectionMapKey.class).path();
            default -> null;
        };
        return raw == null ? null : qualify(prefix, raw);
    }

    private static String qualify(String prefix, String path) {
        return (prefix == null || prefix.isEmpty()) ? path : prefix + "." + path;
    }
}
