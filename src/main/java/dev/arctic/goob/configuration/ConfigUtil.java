package dev.arctic.goob.configuration;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.*;
import org.snakeyaml.engine.v2.api.*;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

/**
 * Central utility for all YAML node-tree operations in Brew's config system.
 *
 * <p>Everything works against SnakeYAML Engine's {@link MappingNode} AST so that
 * comments, formatting, and ordering survive round-trips to disk.
 *
 * <h2>Dot-path convention</h2>
 * All {@code path} parameters are dot-separated keys, e.g. {@code "database.host"}.
 * Intermediate sections are created on demand by setter and inject methods.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>All collections use FastUtil types throughout.</li>
 *   <li>Getters return a typed value or a caller-supplied fallback — never throw.</li>
 *   <li>Setters are upserts: they update an existing key or append a new one.</li>
 *   <li>This class is not instantiable; all methods are static.</li>
 * </ul>
 */
public final class ConfigUtil {

    private ConfigUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ── Load / Save ──────────────────────────────────────────────────────────────

    /**
     * Loads a YAML file into a {@link MappingNode} tree.
     * Returns an empty mapping if the file does not exist or is empty.
     *
     * @param path file to read
     * @return the root mapping node
     * @throws IOException on read failure
     */
    public static MappingNode load(Path path) throws IOException {
        if (!Files.exists(path)) return emptyMapping();

        var settings = LoadSettings.builder()
                .setLabel(path.getFileName().toString())
                .setParseComments(true)
                .build();

        try (var reader = Files.newBufferedReader(path)) {
            var node = new Compose(settings).composeReader(reader);
            if (node.isPresent() && node.get() instanceof MappingNode m) return m;
        }
        return emptyMapping();
    }

    /**
     * Serialises a {@link MappingNode} tree to a YAML file.
     * Parent directories are created automatically.
     *
     * @param root the node tree to write
     * @param path destination file
     * @throws IOException on write failure
     */
    public static void save(MappingNode root, Path path) throws IOException {
        var parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);

        var settings = DumpSettings.builder()
                .setDefaultFlowStyle(FlowStyle.BLOCK)
                .setDumpComments(true)
                .setIndent(2)
                .setIndicatorIndent(2)
                .setBestLineBreak("\n")
                .build();

        try (var writer = Files.newBufferedWriter(path)) {
            new Dump(settings).dumpNode(root, writerAdapter(writer));
        }
    }

    // ── Scalar Getters ───────────────────────────────────────────────────────────

    /**
     * Returns the {@code String} value at {@code path}, or {@code fallback} if absent
     * or not a scalar node.
     */
    public static String getString(MappingNode node, String path, String fallback) {
        return resolveScalar(node, path).orElse(fallback);
    }

    /**
     * Returns the {@code int} value at {@code path}, or {@code fallback} if absent
     * or unparseable.
     */
    public static int getInt(MappingNode node, String path, int fallback) {
        return resolveScalar(node, path).map(v -> {
            try { return Integer.parseInt(v); }
            catch (NumberFormatException e) { return fallback; }
        }).orElse(fallback);
    }

    /**
     * Returns the {@code long} value at {@code path}, or {@code fallback} if absent
     * or unparseable.
     */
    public static long getLong(MappingNode node, String path, long fallback) {
        return resolveScalar(node, path).map(v -> {
            try { return Long.parseLong(v); }
            catch (NumberFormatException e) { return fallback; }
        }).orElse(fallback);
    }

    /**
     * Returns the {@code double} value at {@code path}, or {@code fallback} if absent
     * or unparseable.
     */
    public static double getDouble(MappingNode node, String path, double fallback) {
        return resolveScalar(node, path).map(v -> {
            try { return Double.parseDouble(v); }
            catch (NumberFormatException e) { return fallback; }
        }).orElse(fallback);
    }

    /**
     * Returns the {@code boolean} value at {@code path}, or {@code fallback} if absent.
     * Recognises {@code "true"} and {@code "false"} case-insensitively.
     */
    public static boolean getBoolean(MappingNode node, String path, boolean fallback) {
        return resolveScalar(node, path).map(v -> switch (v.toLowerCase()) {
            case "true"  -> true;
            case "false" -> false;
            default      -> fallback;
        }).orElse(fallback);
    }

    /**
     * Returns the enum constant at {@code path} whose name matches the YAML value
     * (case-insensitive), or {@code fallback} if absent or unrecognised.
     *
     * @param <E>      the enum type
     * @param node     root or section node
     * @param path     dot-separated path
     * @param type     the enum class
     * @param fallback returned when the key is absent or the value doesn't match any constant
     */
    public static <E extends Enum<E>> E getEnum(
            MappingNode node, String path, Class<E> type, E fallback) {
        return resolveScalar(node, path).map(v -> {
            for (var constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(v)) return constant;
            }
            return fallback;
        }).orElse(fallback);
    }

    // ── Scalar Setters ───────────────────────────────────────────────────────────

    /** Upserts a {@code String} scalar at the immediate (non-dotted) {@code key}. */
    public static void setString(MappingNode node, String key, String value) {
        upsertScalar(node, key, value, Tag.STR, ScalarStyle.PLAIN);
    }

    /**
     * Upserts a double-quoted {@code String} scalar at the immediate (non-dotted) {@code key}.
     *
     * <p>Use this for values that contain characters with special YAML meaning, such as
     * hex color codes ({@code #RRGGBB}) where a leading {@code #} would otherwise be
     * parsed as a comment marker.
     */
    public static void setStringQuoted(MappingNode node, String key, String value) {
        upsertScalar(node, key, value, Tag.STR, ScalarStyle.DOUBLE_QUOTED);
    }

    /** Upserts an {@code int} scalar at the immediate (non-dotted) {@code key}. */
    public static void setInt(MappingNode node, String key, int value) {
        upsertScalar(node, key, String.valueOf(value), Tag.INT, ScalarStyle.PLAIN);
    }

    /** Upserts a {@code long} scalar at the immediate (non-dotted) {@code key}. */
    public static void setLong(MappingNode node, String key, long value) {
        upsertScalar(node, key, String.valueOf(value), Tag.INT, ScalarStyle.PLAIN);
    }

    /** Upserts a {@code double} scalar at the immediate (non-dotted) {@code key}. */
    public static void setDouble(MappingNode node, String key, double value) {
        upsertScalar(node, key, String.valueOf(value), Tag.FLOAT, ScalarStyle.PLAIN);
    }

    /** Upserts a {@code boolean} scalar at the immediate (non-dotted) {@code key}. */
    public static void setBoolean(MappingNode node, String key, boolean value) {
        upsertScalar(node, key, String.valueOf(value), Tag.BOOL, ScalarStyle.PLAIN);
    }

    /** Upserts an enum constant (by name) at the immediate (non-dotted) {@code key}. */
    public static void setEnum(MappingNode node, String key, Enum<?> value) {
        upsertScalar(node, key, value.name(), Tag.STR, ScalarStyle.PLAIN);
    }

    /**
     * Upserts a YAML sequence at the immediate (non-dotted) {@code key}.
     * Each element of {@code values} becomes a scalar entry in the sequence.
     * Passing an empty array writes an empty flow-style sequence ({@code []}).
     *
     * <p>The {@code tag} is applied to each scalar element — use {@link Tag#STR},
     * {@link Tag#INT}, or {@link Tag#FLOAT} depending on the element type.
     *
     * @param node   the mapping node to write into
     * @param key    immediate (non-dotted) key name
     * @param values the sequence entries as strings
     * @param tag    YAML tag applied to each scalar element
     */
    public static void setSequence(MappingNode node, String key, String[] values, Tag tag) {
        var items = new ObjectArrayList<Node>(values.length);
        for (var v : values) items.add(new ScalarNode(tag, v, ScalarStyle.PLAIN));

        var flowStyle = values.length == 0 ? FlowStyle.FLOW : FlowStyle.BLOCK;
        var seqNode   = new SequenceNode(Tag.SEQ, items, flowStyle);
        List<NodeTuple> tuples = node.getValue();

        for (int i = 0; i < tuples.size(); i++) {
            var tuple = tuples.get(i);
            if (tuple.getKeyNode() instanceof ScalarNode k && k.getValue().equals(key)) {
                tuples.set(i, new NodeTuple(tuple.getKeyNode(), seqNode));
                return;
            }
        }
        tuples.add(new NodeTuple(new ScalarNode(Tag.STR, key, ScalarStyle.PLAIN), seqNode));
    }

    // ── Section Accessors ────────────────────────────────────────────────────────

    /**
     * Returns the {@link MappingNode} at {@code path}, or {@code null} if absent
     * or not a mapping.
     */
    public static MappingNode getSection(MappingNode node, String path) {
        return resolvePath(node, path)
                .filter(n -> n instanceof MappingNode)
                .map(n -> (MappingNode) n)
                .orElse(null);
    }

    /**
     * Returns the {@link MappingNode} at {@code path}, creating any missing intermediate
     * sections along the way. Never returns {@code null}.
     *
     * @param node root or parent node
     * @param path dot-separated path, e.g. {@code "modules.economy"}
     * @return the existing or newly-created section
     */
    public static MappingNode getOrCreateSection(MappingNode node, String path) {
        var parts   = path.split("\\.", -1);
        var current = node;
        for (var part : parts) {
            var next = getSection(current, part);
            if (next == null) next = createSection(current, part);
            current = next;
        }
        return current;
    }

    /**
     * Creates a new empty section under {@code key} inside {@code parent} and returns it.
     * If a section already exists for {@code key}, the existing node is returned unchanged.
     *
     * @param parent the node to attach the section to
     * @param key    immediate (non-dotted) key name
     * @return the new or existing {@link MappingNode}
     */
    public static MappingNode createSection(MappingNode parent, String key) {
        var existing = getSection(parent, key);
        if (existing != null) return existing;

        var section = emptyMapping();
        parent.getValue().add(new NodeTuple(
                new ScalarNode(Tag.STR, key, ScalarStyle.PLAIN),
                section
        ));
        return section;
    }

    /**
     * Returns a map of all direct child keys of the section at {@code path} whose values
     * are themselves subsections.
     *
     * @param node root or parent node
     * @param path dot-separated path to the parent section
     * @return map of key → {@link MappingNode}; empty if the path doesn't exist
     */
    public static Object2ObjectOpenHashMap<String, MappingNode> getSectionMap(
            MappingNode node, String path) {
        var result  = new Object2ObjectOpenHashMap<String, MappingNode>();
        var section = getSection(node, path);
        if (section == null) return result;

        for (var tuple : section.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key
                    && tuple.getValueNode() instanceof MappingNode value) {
                result.put(key.getValue(), value);
            }
        }
        return result;
    }

    // ── List / Set Getters ───────────────────────────────────────────────────────

    /**
     * Returns a {@code ObjectArrayList<String>} from a YAML sequence at {@code path}.
     * Non-scalar entries are skipped. Returns an empty list if the path is absent.
     */
    public static ObjectArrayList<String> getStringList(MappingNode node, String path) {
        var result = new ObjectArrayList<String>();
        resolveSequence(node, path).ifPresent(seq -> {
            for (var item : seq.getValue()) {
                if (item instanceof ScalarNode s) result.add(s.getValue());
            }
        });
        return result;
    }

    /**
     * Returns an {@code IntArrayList} from a YAML sequence at {@code path}.
     * Non-integer entries are silently skipped.
     */
    public static IntArrayList getIntList(MappingNode node, String path) {
        var result = new IntArrayList();
        resolveSequence(node, path).ifPresent(seq -> {
            for (var item : seq.getValue()) {
                if (item instanceof ScalarNode s) {
                    try { result.add(Integer.parseInt(s.getValue())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        });
        return result;
    }

    /**
     * Returns a {@code DoubleArrayList} from a YAML sequence at {@code path}.
     * Non-numeric entries are silently skipped.
     */
    public static DoubleArrayList getDoubleList(MappingNode node, String path) {
        var result = new DoubleArrayList();
        resolveSequence(node, path).ifPresent(seq -> {
            for (var item : seq.getValue()) {
                if (item instanceof ScalarNode s) {
                    try { result.add(Double.parseDouble(s.getValue())); }
                    catch (NumberFormatException ignored) {}
                }
            }
        });
        return result;
    }

    /**
     * Returns an {@code ObjectOpenHashSet<String>} from a YAML sequence at {@code path}.
     * Duplicate values in the file are collapsed by set semantics.
     */
    public static ObjectOpenHashSet<String> getStringSet(MappingNode node, String path) {
        var result = new ObjectOpenHashSet<String>();
        resolveSequence(node, path).ifPresent(seq -> {
            for (var item : seq.getValue()) {
                if (item instanceof ScalarNode s) result.add(s.getValue());
            }
        });
        return result;
    }

    // ── Schema Merge ─────────────────────────────────────────────────────────────

    /**
     * Recursively merges {@code defaults} into {@code existing}, adding any keys that
     * are present in {@code defaults} but absent in {@code existing}. Existing values
     * are never overwritten.
     *
     * @param existing the user's current config tree (modified in-place)
     * @param defaults the bundled default tree to merge from
     * @return {@code existing} (for chaining)
     */
    public static MappingNode mergeDefaults(MappingNode existing, MappingNode defaults) {
        for (var defaultTuple : defaults.getValue()) {
            if (!(defaultTuple.getKeyNode() instanceof ScalarNode defaultKey)) continue;

            var key           = defaultKey.getValue();
            var existingTuple = findTuple(existing, key);

            if (existingTuple.isEmpty()) {
                existing.getValue().add(defaultTuple);
            } else if (defaultTuple.getValueNode() instanceof MappingNode defaultSection
                    && existingTuple.get().getValueNode() instanceof MappingNode existingSection) {
                mergeDefaults(existingSection, defaultSection);
            }
        }
        return existing;
    }

    // ── Comment Writing ──────────────────────────────────────────────────────────

    /**
     * Attaches comment lines above the key node at {@code path}.
     * Each element of {@code lines} becomes a separate {@code # …} comment line.
     * Has no effect if the key does not exist in the tree.
     *
     * @param root  the root mapping node
     * @param path  dot-separated path to the key to annotate
     * @param lines comment text lines (leading {@code #} added automatically)
     */
    public static void setComment(MappingNode root, String path, String... lines) {
        var parts   = path.split("\\.", -1);
        var current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            current = getSection(current, parts[i]);
            if (current == null) return;
        }

        var leafKey = parts[parts.length - 1];
        for (var tuple : current.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode keyNode
                    && keyNode.getValue().equals(leafKey)) {
                var comments = new ObjectArrayList<CommentLine>();
                for (var line : lines) {
                    comments.add(new CommentLine(
                            java.util.Optional.empty(),
                            java.util.Optional.empty(),
                            " " + line,
                            CommentType.BLOCK
                    ));
                }
                keyNode.setBlockComments(comments);
                return;
            }
        }
    }

    // ── Node Inspection ──────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a value (of any type) exists at {@code path}.
     */
    public static boolean contains(MappingNode node, String path) {
        return resolvePath(node, path).isPresent();
    }

    /**
     * Returns all immediate child keys of the section at {@code path}.
     * Returns an empty list if the path is absent or not a mapping.
     */
    public static ObjectArrayList<String> getKeys(MappingNode node, String path) {
        var result  = new ObjectArrayList<String>();
        var section = path.isEmpty() ? node : getSection(node, path);
        if (section == null) return result;

        for (var tuple : section.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode key) {
                result.add(key.getValue());
            }
        }
        return result;
    }

    /**
     * Removes the key at {@code path} from the tree.
     * Does nothing if the key does not exist.
     *
     * @param node root or parent node
     * @param path dot-separated path
     */
    public static void remove(MappingNode node, String path) {
        var parts   = path.split("\\.", -1);
        var current = node;

        for (int i = 0; i < parts.length - 1; i++) {
            current = getSection(current, parts[i]);
            if (current == null) return;
        }

        var leafKey = parts[parts.length - 1];
        current.getValue().removeIf(tuple ->
                tuple.getKeyNode() instanceof ScalarNode key
                        && key.getValue().equals(leafKey));
    }

    // ── Internal Helpers ─────────────────────────────────────────────────────────

    /**
     * Resolves a dot-path to the raw {@link Node} at its tip, or empty if any
     * intermediate key is missing.
     */
    static Optional<Node> resolvePath(MappingNode node, String path) {
        if (node == null || path == null || path.isEmpty()) return Optional.empty();
        var parts   = path.split("\\.", -1);
        var current = node;
        for (int i = 0; i < parts.length - 1; i++) {
            var next = findTuple(current, parts[i])
                    .map(NodeTuple::getValueNode)
                    .filter(n -> n instanceof MappingNode)
                    .map(n -> (MappingNode) n)
                    .orElse(null);
            if (next == null) return Optional.empty();
            current = next;
        }
        return findTuple(current, parts[parts.length - 1]).map(NodeTuple::getValueNode);
    }

    /** Resolves a dot-path to a scalar value string, or empty if absent or non-scalar. */
    private static Optional<String> resolveScalar(MappingNode node, String path) {
        return resolvePath(node, path)
                .filter(n -> n instanceof ScalarNode)
                .map(n -> ((ScalarNode) n).getValue());
    }

    /** Resolves a dot-path to a {@link SequenceNode}, or empty if absent or not a sequence. */
    private static Optional<SequenceNode> resolveSequence(MappingNode node, String path) {
        return resolvePath(node, path)
                .filter(n -> n instanceof SequenceNode)
                .map(n -> (SequenceNode) n);
    }

    /** Finds a direct child {@link NodeTuple} by key name, or empty if not found. */
    static Optional<NodeTuple> findTuple(MappingNode node, String key) {
        if (node == null) return Optional.empty();
        for (var tuple : node.getValue()) {
            if (tuple.getKeyNode() instanceof ScalarNode k && k.getValue().equals(key)) {
                return Optional.of(tuple);
            }
        }
        return Optional.empty();
    }

    /**
     * Upserts a scalar under an immediate (non-dotted) key within {@code node}.
     * Replaces the value node in-place if the key exists, appends if not.
     *
     * @param style {@link ScalarStyle#PLAIN} for normal values;
     *              {@link ScalarStyle#DOUBLE_QUOTED} for values that contain YAML special
     *              characters (e.g. hex colors starting with {@code #})
     */
    static void upsertScalar(MappingNode node, String key, String value, Tag tag, ScalarStyle style) {
        var newValue = new ScalarNode(tag, value, style);
        List<NodeTuple> tuples = node.getValue();

        for (int i = 0; i < tuples.size(); i++) {
            var tuple = tuples.get(i);
            if (tuple.getKeyNode() instanceof ScalarNode k && k.getValue().equals(key)) {
                tuples.set(i, new NodeTuple(tuple.getKeyNode(), newValue));
                return;
            }
        }
        tuples.add(new NodeTuple(new ScalarNode(Tag.STR, key, ScalarStyle.PLAIN), newValue));
    }

    /** Returns a new empty block-style {@link MappingNode}. */
    static MappingNode emptyMapping() {
        return new MappingNode(Tag.MAP, new ObjectArrayList<>(), FlowStyle.BLOCK);
    }

    /** Adapts a {@link Writer} to the SnakeYAML Engine {@link StreamDataWriter} interface. */
    private static StreamDataWriter writerAdapter(Writer writer) {
        return new StreamDataWriter() {
            @Override public void write(String s)                   { try { writer.write(s); }          catch (IOException e) { throw new RuntimeException(e); } }
            @Override public void write(String s, int off, int len) { try { writer.write(s, off, len); } catch (IOException e) { throw new RuntimeException(e); } }
            @Override public void flush()                           { try { writer.flush(); }            catch (IOException e) { throw new RuntimeException(e); } }
        };
    }
}
