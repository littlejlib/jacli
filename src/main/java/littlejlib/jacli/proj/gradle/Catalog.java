package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Coords;

/**
 * {@code gradle/libs.versions.toml}, read only far enough to turn a {@code libs.asm.commons} accessor
 * back into coordinates. Needed because a modern gradle build spells almost no coordinates in the
 * build script itself, so a reader that ignores the catalog sees a project with no dependencies.
 * <p>
 * Accessors flatten {@code -} and {@code _} to {@code .}, which is gradle's own mapping, so the key
 * {@code asm-commons} and the accessor {@code libs.asm.commons} meet in the middle.
 */
public record Catalog(Map<String, String> versions, Map<String, Coords> libraries) {

    public static final Catalog EMPTY = new Catalog(Map.of(), Map.of());
    public static final String DEFAULT = "gradle/libs.versions.toml";

    public static Catalog of(Path root) {
        var file = root.resolve(DEFAULT);
        if (!Files.isRegularFile(file)) return EMPTY;
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return EMPTY;
        }
    }

    public boolean isEmpty() { return libraries.isEmpty() && versions.isEmpty(); }

    public static String accessor(String key) { return key.replace('-', '.').replace('_', '.'); }

    /** {@code libs.asm.commons} arrives here as {@code asm.commons}. */
    public Coords lookup(String path) { return libraries.get(accessor(path)); }

    public static Catalog parse(String toml) {
        var versions = new LinkedHashMap<String, String>();
        var raw = new LinkedHashMap<String, Map<String, String>>();
        var inline = new LinkedHashMap<String, String>();
        var section = "";
        var table = (String) null;
        for (var line : toml.lines().map(Catalog::uncomment).toList()) {
            if (line.isEmpty()) continue;
            if (line.startsWith("[")) {
                var header = line.substring(1, Math.max(1, line.indexOf(']')));
                var dot = header.indexOf('.');
                section = dot < 0 ? header : header.substring(0, dot);
                table = dot < 0 ? null : header.substring(dot + 1);
                if (table != null) raw.computeIfAbsent(accessor(table), k -> new LinkedHashMap<>());
                continue;
            }
            var eq = line.indexOf('=');
            if (eq < 0) continue;
            var key = line.substring(0, eq).trim();
            var value = line.substring(eq + 1).trim();
            if (table != null) raw.get(accessor(table)).put(key, Dsl.unquote(value));
            else if ("versions".equals(section)) versions.put(accessor(key), Dsl.unquote(value));
            else if ("libraries".equals(section)) inline.put(accessor(key), value);
        }
        var libs = new LinkedHashMap<String, Coords>();
        inline.forEach((k, v) -> put(libs, k, v.startsWith("{") ? fields(v) : Map.of("__gav", Dsl.unquote(v)), versions));
        raw.forEach((k, fields) -> put(libs, k, fields, versions));
        return new Catalog(Map.copyOf(versions), Map.copyOf(libs));
    }

    static String uncomment(String line) {
        var s = line.trim();
        var hash = -1;
        var quote = (char) 0;
        for (var i = 0; i < s.length() && hash < 0; i++) {
            var c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '#') hash = i;
        }
        return (hash < 0 ? s : s.substring(0, hash)).trim();
    }

    /** {@code { module = "g:a", version.ref = "x" }} flattened to its keys. */
    static Map<String, String> fields(String inlineTable) {
        var out = new LinkedHashMap<String, String>();
        var body = inlineTable.substring(1, Math.max(1, inlineTable.lastIndexOf('}')));
        for (var part : split(body)) {
            var eq = part.indexOf('=');
            if (eq < 0) continue;
            var k = part.substring(0, eq).trim();
            var v = part.substring(eq + 1).trim();
            if (v.startsWith("{")) {
                fields(v).forEach((nk, nv) -> out.put(k + "." + nk, nv));
                continue;
            }
            out.put(k, Dsl.unquote(v));
        }
        return out;
    }

    /** Commas at brace depth zero and outside quotes. */
    static List<String> split(String body) {
        var out = new ArrayList<String>();
        var depth = 0;
        var start = 0;
        var quote = (char) 0;
        for (var i = 0; i < body.length(); i++) {
            var c = body.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == ',' && depth == 0) {
                out.add(body.substring(start, i));
                start = i + 1;
            }
        }
        out.add(body.substring(start));
        return out;
    }

    static void put(Map<String, Coords> libs, String key, Map<String, String> f, Map<String, String> versions) {
        var gav = f.get("__gav");
        if (gav != null) {
            var c = Coords.parse(gav);
            if (!"unknown".equals(c.artifactId())) libs.put(key, c);
            return;
        }
        var module = f.get("module");
        var group = module != null && module.contains(":") ? module.substring(0, module.indexOf(':')) : f.get("group");
        var name = module != null && module.contains(":") ? module.substring(module.indexOf(':') + 1) : f.get("name");
        if (group == null || name == null) return;
        var ref = f.get("version.ref");
        var version = ref != null ? versions.get(accessor(ref))
                : f.getOrDefault("version", f.get("version.require"));
        libs.put(key, new Coords(group, name, version == null ? "" : version));
    }
}
