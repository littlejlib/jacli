package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Coords;

/**
 * Dependencies read out of the build scripts, for when gradle cannot be run.
 * <p>
 * Two readings, because the two questions have opposite failure costs. A classpath wants every
 * coordinate the file mentions: an extra jar costs javac a little time, a missing one costs
 * resolution. {@code deps} and {@code releasable} want only what is declared on a compile
 * configuration, because reporting a test-only artifact as a release blocker is a false alarm — the
 * same false alarm the maven side already had to fix for optional dependencies.
 */
public final class Deps {

    private Deps() { }

    public static final List<String> COMPILE = List.of("implementation", "api", "compileOnly",
            "compileOnlyApi", "annotationProcessor", "runtimeOnly", "providedCompile", "kapt", "ksp");
    public static final List<String> TESTS = List.of("testImplementation", "testApi", "testCompileOnly",
            "testRuntimeOnly", "testAnnotationProcessor", "androidTestImplementation");

    static final Pattern GAV = Pattern.compile("[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+(:[^\\s:]+)?");
    static final Pattern ACCESSOR = Pattern.compile("\\blibs\\.([A-Za-z0-9_.]+)");
    static final Set<String> NOT_A_LIBRARY = Set.of("versions", "plugins", "bundles");

    /** Everything that looks like a coordinate anywhere in the build files. */
    public static List<Coords> mentioned(List<Path> buildFiles, Catalog catalog) {
        var out = new LinkedHashMap<String, Coords>();
        for (var file : buildFiles) {
            var src = Dsl.read(file);
            Dsl.literals(src).forEach(lit -> addGav(out, lit));
            accessors(src, catalog, out);
        }
        return List.copyOf(out.values());
    }

    /** Only what is declared on a configuration a consumer of this project would inherit. */
    public static List<Coords> declared(List<Path> buildFiles, Catalog catalog, boolean includeTests) {
        var configurations = new ArrayList<>(COMPILE);
        if (includeTests) configurations.addAll(TESTS);
        var out = new LinkedHashMap<String, Coords>();
        for (var file : buildFiles) {
            var src = Dsl.read(file);
            for (var conf : configurations)
                for (var at : Dsl.occurrences(src, conf)) {
                    var text = Dsl.callText(src, at + conf.length());
                    Dsl.literals(text).forEach(lit -> addGav(out, lit));
                    accessors(text, catalog, out);
                }
        }
        return List.copyOf(out.values());
    }

    /** Sibling projects a build script depends on, as gradle paths. */
    public static List<String> projectRefs(List<Path> buildFiles) {
        var out = new LinkedHashSet<String>();
        for (var file : buildFiles) {
            var src = Dsl.read(file);
            for (var at : Dsl.occurrences(src, "project"))
                Dsl.args(src, at + "project".length()).stream().filter(a -> a.startsWith(":")).forEach(out::add);
        }
        return List.copyOf(out);
    }

    static void accessors(String src, Catalog catalog, Map<String, Coords> out) {
        var m = ACCESSOR.matcher(src);
        while (m.find()) {
            var path = m.group(1);
            if (NOT_A_LIBRARY.contains(head(path))) continue;
            var c = catalog.lookup(path);
            if (c != null) out.putIfAbsent(c.groupId() + ":" + c.artifactId(), c);
        }
    }

    static String head(String dotted) {
        var dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    static void addGav(Map<String, Coords> out, String literal) {
        var s = literal.trim();
        if (s.startsWith(":") || s.contains("/") || s.contains(" ") || !GAV.matcher(s).matches()) return;
        var c = Coords.parse(s);
        if ("unknown".equals(c.artifactId()) || c.version() == null || c.version().isBlank()) return;
        out.putIfAbsent(c.groupId() + ":" + c.artifactId(), c);
    }
}
