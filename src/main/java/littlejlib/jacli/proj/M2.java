package littlejlib.jacli.proj;

import module java.base;

/**
 * Fallback classpath assembly straight from {@code ~/.m2/repository}, for when maven cannot be run
 * (offline with a cold repository, no maven on PATH, a pom maven itself rejects). Deliberately
 * approximate — it ignores profiles, dependency management and version ranges. An approximate
 * classpath costs accuracy on external types and reports as {@code PARTIAL}, which beats refusing
 * to answer.
 */
public final class M2 {

    private M2() { }

    public static final int MAX_DEPTH = 3;

    public static Path repo() {
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    public static List<Path> classpathFor(List<Path> poms, Consumer<String> log) {
        var jars = new LinkedHashMap<String, Path>();
        var seen = new HashSet<String>();
        for (var pom : poms) collect(Poms.directDependencies(pom), jars, seen, 0, log);
        return List.copyOf(jars.values());
    }

    static void collect(List<String> gavs, Map<String, Path> jars, Set<String> seen, int depth, Consumer<String> log) {
        if (depth > MAX_DEPTH) return;
        for (var gav : gavs) {
            var c = Coords.parse(gav);
            if (c.version() == null || c.version().isEmpty()) continue;
            var ga = c.groupId() + ":" + c.artifactId();
            if (!seen.add(ga)) continue;
            var jar = jarPath(c);
            if (jar != null && Files.isRegularFile(jar)) {
                jars.put(ga, jar);
                var pom = jar.getParent().resolve(c.artifactId() + "-" + c.version() + ".pom");
                if (Files.isRegularFile(pom)) collect(Poms.directDependencies(pom), jars, seen, depth + 1, log);
            } else {
                log.accept("not in local m2: " + c);
            }
        }
    }

    public static Path jarPath(Coords c) {
        if (c.version() == null) return null;
        var p = repo();
        for (var seg : c.groupId().split("\\.")) p = p.resolve(seg);
        return p.resolve(c.artifactId()).resolve(c.version())
                .resolve(c.artifactId() + "-" + c.version() + ".jar");
    }
}
