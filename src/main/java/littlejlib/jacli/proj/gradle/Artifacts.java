package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Coords;
import littlejlib.jacli.proj.M2;
import littlejlib.jacli.proj.Poms;

/**
 * Finding an artifact on this machine when gradle is not available to be asked. Gradle's own cache
 * first, then the maven repository, because a build declaring {@code mavenLocal()} genuinely resolves
 * from there and pretending otherwise would report a working build as broken.
 * <p>
 * The closure is walked breadth-first for the same nearest-wins reason the maven side is, and it can
 * only follow modules published with a pom: a library published with gradle module metadata alone
 * stops the walk there, which understates the closure rather than inventing it.
 */
public final class Artifacts {

    private Artifacts() { }

    public static final int MAX_DEPTH = 4;

    public static Path jar(Coords c) {
        var cached = GradleCache.jar(c);
        if (cached != null) return cached;
        var m2 = M2.jarPath(c);
        return m2 != null && Files.isRegularFile(m2) ? m2 : null;
    }

    public static Path pom(Coords c) {
        var cached = GradleCache.pom(c);
        if (cached != null) return cached;
        var m2 = M2.jarPath(c);
        if (m2 == null) return null;
        var pom = m2.getParent().resolve(c.artifactId() + "-" + c.version() + ".pom");
        return Files.isRegularFile(pom) ? pom : null;
    }

    public static boolean inGradleCache(Coords c) { return GradleCache.present(c); }

    public static boolean inM2(Coords c) {
        var m2 = M2.jarPath(c);
        return m2 != null && Files.isRegularFile(m2);
    }

    public static List<GradleDep> closure(List<Coords> direct, int maxDepth) {
        var depth = Math.min(maxDepth, MAX_DEPTH);
        var winners = new LinkedHashMap<String, GradleDep>();
        var frontier = new ArrayList<>(direct);
        for (var level = 1; level <= depth && !frontier.isEmpty(); level++) {
            var next = new ArrayList<Coords>();
            for (var c : frontier) {
                if (c.version() == null || c.version().isBlank()) continue;
                var key = c.groupId() + ":" + c.artifactId();
                if (winners.containsKey(key)) continue;
                winners.put(key, new GradleDep(level, c));
                if (level >= depth) continue;
                var pom = pom(c);
                if (pom != null)
                    for (var gav : Poms.directDependencies(pom)) {
                        var child = Coords.parse(gav);
                        if (child.version() != null && !child.version().isBlank()) next.add(child);
                    }
            }
            frontier = next;
        }
        return List.copyOf(winners.values());
    }

    public static List<String> jars(Collection<GradleDep> deps) {
        var out = new LinkedHashSet<String>();
        for (var d : deps) {
            var jar = jar(d.coords());
            if (jar != null) out.add(jar.toString());
        }
        return List.copyOf(out);
    }
}
