package littlejlib.jacli.proj;

import module java.base;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

/**
 * Pom reading with jsoup — no Aether, so no network round trip per dependency. Coordinates,
 * modules and direct dependencies are all that is needed; the full transitive closure comes from
 * {@code mvn dependency:build-classpath}, and only the fallback in {@link Classpath} walks poms.
 */
public final class Poms {

    private Poms() { }

    public static final String POM = "pom.xml";

    public static Document read(Path pom) {
        try {
            return Jsoup.parse(Files.readString(pom, StandardCharsets.UTF_8), "", Parser.xmlParser());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Coords coords(Path pom) {
        if (!Files.isRegularFile(pom)) return Coords.UNKNOWN;
        var doc = read(pom);
        var project = doc.selectFirst("project");
        if (project == null) return Coords.UNKNOWN;
        var g = own(project, "groupId");
        var a = own(project, "artifactId");
        var v = own(project, "version");
        var parent = project.selectFirst("> parent");
        if (g == null && parent != null) g = text(parent, "groupId");
        if (v == null && parent != null) v = text(parent, "version");
        return new Coords(g == null ? "unknown" : g, a == null ? "unknown" : a, v == null ? "0" : v);
    }

    public static List<String> modules(Path pom) {
        if (!Files.isRegularFile(pom)) return List.of();
        return read(pom).select("project > modules > module").stream().map(e -> e.text().trim())
                .filter(s -> !s.isEmpty()).toList();
    }

    /**
     * {@code groupId:artifactId:version[:type]} for each direct dependency that a consumer would
     * actually need. Test, provided and optional dependencies are excluded: an optional dependency is
     * by definition not required to build against this artifact, and reporting one as a blocker was a
     * false alarm.
     */
    public static List<String> directDependencies(Path pom) {
        if (!Files.isRegularFile(pom)) return List.of();
        var doc = read(pom);
        var props = properties(doc);
        var out = new ArrayList<String>();
        for (var d : doc.select("project > dependencies > dependency")) {
            var scope = text(d, "scope");
            if ("test".equals(scope) || "provided".equals(scope) || "system".equals(scope)) continue;
            if ("true".equalsIgnoreCase(text(d, "optional"))) continue;
            var g = expand(text(d, "groupId"), props);
            var a = expand(text(d, "artifactId"), props);
            var v = expand(text(d, "version"), props);
            var type = text(d, "type");
            if (g != null && a != null)
                out.add(g + ":" + a + ":" + (v == null ? "" : v) + (type == null ? "" : ":" + type));
        }
        return out;
    }

    public static Map<String, String> properties(Document doc) {
        var m = new HashMap<String, String>();
        var props = doc.selectFirst("project > properties");
        if (props != null) props.children().forEach(c -> m.put(c.tagName(), c.text().trim()));
        var project = doc.selectFirst("project");
        if (project != null) {
            var v = own(project, "version");
            if (v != null) m.put("project.version", v);
            var g = own(project, "groupId");
            if (g != null) m.put("project.groupId", g);
        }
        return m;
    }

    static String expand(String value, Map<String, String> props) {
        if (value == null || !value.contains("${")) return value;
        var out = value;
        for (var i = 0; i < 4 && out.contains("${"); i++)
            for (var e : props.entrySet()) out = out.replace("${" + e.getKey() + "}", e.getValue());
        return out.contains("${") ? null : out;
    }

    static String own(org.jsoup.nodes.Element project, String tag) {
        var e = project.selectFirst("> " + tag);
        return e == null || e.text().isBlank() ? null : e.text().trim();
    }

    static String text(org.jsoup.nodes.Element parent, String tag) {
        var e = parent.selectFirst("> " + tag);
        return e == null || e.text().isBlank() ? null : e.text().trim();
    }
}
