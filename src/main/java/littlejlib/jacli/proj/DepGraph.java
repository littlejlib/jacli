package littlejlib.jacli.proj;

import module java.base;

/**
 * The dependency closure, read from poms in the local repository. No network, no Aether: enough to
 * answer what a project depends on and whether anyone else could build it.
 * <p>
 * Walked BREADTH-FIRST, because maven resolves a version conflict by nearest-wins and a depth-first
 * walk claims the key at whatever depth it happens to reach first. That is not a detail: a depth-first
 * walk of this very project reported {@code jackson-databind:2.9.10.5}, a version declared by a
 * transitive pom that maven would never resolve, as a missing dependency.
 * <p>
 * Still approximate, and it says so — profiles, dependencyManagement and version ranges are not
 * expanded. For the releasability question that is sufficient: an artifact that is absent or
 * local-only is absent or local-only however its version was chosen.
 */
public final class DepGraph {

    private DepGraph() { }

    public static final int DEFAULT_DEPTH = 3, MAX_DEPTH = 12;

    /**
     * Gradle answers from its model rather than from poms, and never launches gradle here: this is
     * called once per project by {@code scope --suggest}, so a build tool run per project would turn
     * one suggestion into minutes. {@code deps} and {@code releasable} ask for the real thing.
     */
    public static List<DepNode> of(Path root, List<Path> moduleRoots, int depth, Consumer<String> log) {
        if (Builds.kindOf(root) == BuildKind.GRADLE)
            return littlejlib.jacli.proj.gradle.GradleDeps.of(root, depth, false, log);
        var maxDepth = Math.min(depth, MAX_DEPTH);
        var winners = new LinkedHashMap<String, DepNode>();
        var frontier = new ArrayList<Coords>();
        for (var pom : Classpath.poms(root, moduleRoots))
            for (var gav : Poms.directDependencies(pom)) add(frontier, gav, log);

        for (var level = 0; level <= maxDepth && !frontier.isEmpty(); level++) {
            var next = new ArrayList<Coords>();
            for (var c : frontier) {
                var key = c.groupId() + ":" + c.artifactId();
                if (winners.containsKey(key)) continue;
                var origin = Origin.of(c);
                winners.put(key, new DepNode(c, level, origin));
                if (level >= maxDepth || origin.isMissing()) continue;
                var artifact = Origin.artifactPath(c);
                var pom = artifact.getParent().resolve(c.artifactId() + "-" + c.version() + ".pom");
                if (Files.isRegularFile(pom))
                    for (var gav : Poms.directDependencies(pom)) add(next, gav, log);
            }
            frontier = next;
        }
        return List.copyOf(winners.values());
    }

    static void add(List<Coords> into, String gav, Consumer<String> log) {
        var c = Coords.parse(gav);
        if (c.version() == null || c.version().isEmpty()) {
            log.accept("version unresolved for " + c.groupId() + ":" + c.artifactId()
                    + " (a property or dependencyManagement entry this reader does not expand)");
            return;
        }
        into.add(c);
    }

    /** Indented by depth. Each artifact appears once, because nearest-wins already chose its version. */
    public static String render(List<DepNode> nodes) {
        var sb = new StringBuilder();
        for (var n : nodes)
            sb.append("  ".repeat(n.depth() + 1)).append(n.coords())
                    .append("   ").append(n.origin().label()).append('\n');
        return sb.toString();
    }

    /** Which of the given project roots declare a dependency on {@code groupId:artifactId}. */
    public static List<String> dependentsAmong(Collection<Path> projectRoots, String groupArtifact) {
        var out = new ArrayList<String>();
        for (var root : projectRoots) {
            if (!Files.isDirectory(root)) continue;
            if (Builds.kindOf(root) == BuildKind.GRADLE) {
                if (littlejlib.jacli.proj.gradle.GradleDeps.declares(root, groupArtifact)) out.add(root.toString());
                continue;
            }
            for (var pom : Classpath.poms(root, Projects.moduleRoots(root)))
                if (Poms.directDependencies(pom).stream().anyMatch(g -> g.startsWith(groupArtifact + ":"))) {
                    out.add(root.toString());
                    break;
                }
        }
        return out;
    }
}
