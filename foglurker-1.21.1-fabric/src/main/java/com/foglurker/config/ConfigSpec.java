package com.foglurker.config;

import com.foglurker.FogLurkerMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A small stand-in for Forge's config spec (Fabric has none): typed values with defaults, ranges and comments,
 * kept in a TOML file with [sections] and {@code key = value} lines. Values read from the file that are missing,
 * malformed or out of range fall back to the default, and the file is rewritten with every key and comment.
 */
public final class ConfigSpec {
    private final List<ConfigValue<?>> values;
    private Path file;

    private ConfigSpec(List<ConfigValue<?>> values) {
        this.values = values;
    }

    /** Reads the file (writing a complete one back), creating it with the defaults if it is not there. */
    public synchronized void load(Path path) {
        this.file = path;
        Map<String, String> raw = new LinkedHashMap<>();
        if (Files.isRegularFile(path)) {
            try {
                String section = "";
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String l = line.strip();
                    if (l.isEmpty() || l.startsWith("#")) {
                        continue;
                    }
                    if (l.startsWith("[") && l.endsWith("]")) {
                        section = l.substring(1, l.length() - 1).strip();
                        continue;
                    }
                    int eq = l.indexOf('=');
                    if (eq > 0) {
                        String key = l.substring(0, eq).strip();
                        raw.put(section.isEmpty() ? key : section + "." + key, l.substring(eq + 1).strip());
                    }
                }
            } catch (IOException | RuntimeException e) {
                FogLurkerMod.LOGGER.warn("Fog Lurker: could not read {} ({}); using the defaults", path, e.toString());
            }
        }
        for (ConfigValue<?> v : values) {
            String text = raw.get(v.path);
            if (text != null && !v.parse(text)) {
                FogLurkerMod.LOGGER.warn("Fog Lurker: {} = {} in {} is not allowed; using {}", v.path, text, path.getFileName(),
                        v.format());
            }
        }
        save();
    }

    /** Writes every value (with its comment) back to the file. */
    public synchronized void save() {
        if (file == null) {
            return;
        }
        StringBuilder out = new StringBuilder();
        String section = null;
        for (ConfigValue<?> v : values) {
            int dot = v.path.lastIndexOf('.');
            String sec = dot < 0 ? "" : v.path.substring(0, dot);
            if (!sec.equals(section)) {
                section = sec;
                if (!sec.isEmpty()) {
                    out.append(out.length() == 0 ? "" : "\n").append('[').append(sec).append("]\n");
                }
            }
            for (String c : v.comments) {
                out.append("# ").append(c).append('\n');
            }
            if (v.rangeComment() != null) {
                out.append("# ").append(v.rangeComment()).append('\n');
            }
            out.append(dot < 0 ? v.path : v.path.substring(dot + 1)).append(" = ").append(v.format()).append("\n\n");
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FogLurkerMod.LOGGER.warn("Fog Lurker: could not write {} ({})", file, e.toString());
        }
    }

    public static final class Builder {
        private final List<String> sections = new ArrayList<>();
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private List<String> pendingComments = new ArrayList<>();

        public Builder push(String section) {
            sections.add(section);
            return this;
        }

        public Builder pop() {
            sections.remove(sections.size() - 1);
            return this;
        }

        public Builder comment(String... lines) {
            pendingComments = new ArrayList<>(List.of(lines));
            return this;
        }

        private <V extends ConfigValue<?>> V add(V v) {
            v.comments = pendingComments;
            pendingComments = new ArrayList<>();
            values.add(v);
            return v;
        }

        private String path(String key) {
            return sections.isEmpty() ? key : String.join(".", sections) + "." + key;
        }

        public BooleanValue define(String key, boolean def) {
            return add(new BooleanValue(path(key), def));
        }

        public ConfigValue<String> define(String key, String def) {
            return add(new StringValue(path(key), def, o -> true));
        }

        public ConfigValue<String> define(String key, String def, Predicate<Object> valid) {
            return add(new StringValue(path(key), def, valid));
        }

        public IntValue defineInRange(String key, int def, int min, int max) {
            return add(new IntValue(path(key), def, min, max));
        }

        public DoubleValue defineInRange(String key, double def, double min, double max) {
            return add(new DoubleValue(path(key), def, min, max));
        }

        public ConfigValue<List<? extends String>> defineList(String key, List<? extends String> def, Predicate<Object> valid) {
            return add(new StringListValue(path(key), def, valid));
        }

        public <E extends Enum<E>> EnumValue<E> defineEnum(String key, E def) {
            return add(new EnumValue<>(path(key), def));
        }

        public ConfigSpec build() {
            return new ConfigSpec(List.copyOf(values));
        }
    }

    public abstract static class ConfigValue<T> {
        final String path;
        List<String> comments = List.of();
        protected volatile T value;
        private final T def;

        ConfigValue(String path, T def) {
            this.path = path;
            this.def = def;
            this.value = def;
        }

        public T get() {
            return value;
        }

        public T getDefault() {
            return def;
        }

        public void set(T v) {
            value = v;
        }

        /** @return false (and keeps the default) if the text is not a valid value */
        abstract boolean parse(String text);

        abstract String format();

        String rangeComment() {
            return null;
        }
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String path, boolean def) {
            super(path, def);
        }

        @Override
        boolean parse(String text) {
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                value = Boolean.parseBoolean(text.toLowerCase(Locale.ROOT));
                return true;
            }
            return false;
        }

        @Override
        String format() {
            return String.valueOf(value);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {
        private final int min;
        private final int max;

        IntValue(String path, int def, int min, int max) {
            super(path, def);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean parse(String text) {
            try {
                int v = Integer.parseInt(text);
                if (v >= min && v <= max) {
                    value = v;
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // not a number
            }
            return false;
        }

        @Override
        String format() {
            return String.valueOf(value);
        }

        @Override
        String rangeComment() {
            return "Range: " + min + " ~ " + max;
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {
        private final double min;
        private final double max;

        DoubleValue(String path, double def, double min, double max) {
            super(path, def);
            this.min = min;
            this.max = max;
        }

        @Override
        boolean parse(String text) {
            try {
                double v = Double.parseDouble(text);
                if (v >= min && v <= max) {
                    value = v;
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // not a number
            }
            return false;
        }

        @Override
        String format() {
            return String.valueOf(value);
        }

        @Override
        String rangeComment() {
            return "Range: " + min + " ~ " + max;
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends ConfigValue<E> {
        private final Class<E> type;

        EnumValue(String path, E def) {
            super(path, def);
            this.type = def.getDeclaringClass();
        }

        @Override
        boolean parse(String text) {
            String name = unquote(text);
            for (E e : type.getEnumConstants()) {
                if (e.name().equalsIgnoreCase(name)) {
                    value = e;
                    return true;
                }
            }
            return false;
        }

        @Override
        String format() {
            return quote(value.name());
        }

        @Override
        String rangeComment() {
            List<String> names = new ArrayList<>();
            for (E e : type.getEnumConstants()) {
                names.add(e.name());
            }
            return "Allowed values: " + String.join(", ", names);
        }
    }

    static final class StringValue extends ConfigValue<String> {
        private final Predicate<Object> valid;

        StringValue(String path, String def, Predicate<Object> valid) {
            super(path, def);
            this.valid = valid;
        }

        @Override
        boolean parse(String text) {
            if (!text.startsWith("\"")) {
                return false;
            }
            String s = unquote(text);
            if (valid.test(s)) {
                value = s;
                return true;
            }
            return false;
        }

        @Override
        String format() {
            return quote(value);
        }
    }

    static final class StringListValue extends ConfigValue<List<? extends String>> {
        private final Predicate<Object> valid;

        StringListValue(String path, List<? extends String> def, Predicate<Object> valid) {
            super(path, List.copyOf(def));
            this.valid = valid;
        }

        @Override
        boolean parse(String text) {
            String t = text.strip();
            if (!t.startsWith("[") || !t.endsWith("]")) {
                return false;
            }
            List<String> out = new ArrayList<>();
            String inner = t.substring(1, t.length() - 1).strip();
            // Quoted items, separated by commas (a comma inside the quotes is part of the item).
            int i = 0;
            while (i < inner.length()) {
                char c = inner.charAt(i);
                if (c == ',' || Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c != '"') {
                    return false;
                }
                StringBuilder item = new StringBuilder();
                int j = i + 1;
                boolean closed = false;
                while (j < inner.length()) {
                    char d = inner.charAt(j);
                    if (d == '\\' && j + 1 < inner.length() && (inner.charAt(j + 1) == '"' || inner.charAt(j + 1) == '\\')) {
                        item.append(inner.charAt(j + 1));
                        j += 2;
                    } else if (d == '"') {
                        closed = true;
                        j++;
                        break;
                    } else {
                        item.append(d);
                        j++;
                    }
                }
                if (!closed || !valid.test(item.toString())) {
                    return false;
                }
                out.add(item.toString());
                i = j;
            }
            value = List.copyOf(out);
            return true;
        }

        @Override
        String format() {
            List<String> parts = new ArrayList<>();
            for (String s : value) {
                parts.add(quote(s));
            }
            return "[" + String.join(", ", parts) + "]";
        }
    }

    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String unquote(String s) {
        String t = s.strip();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
