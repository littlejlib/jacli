package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.*;

/**
 * {@code deps} and {@code releasable} for a gradle build.
 * <p>
 * The graph is gradle's own resolution result when gradle could be run, which is strictly better than
 * what the maven side gets: real nearest-wins versions rather than a pom walk that skips profiles and
 * dependencyManagement. Otherwise it is the closure of what the build files declare, walked through
 * whatever poms the caches hold.
 * <p>
 * Origins differ from maven's in one way that matters and is not a limitation of this reader. Maven
 * records the repository each artifact came from in {@code _remote.repositories}; gradle records
 * nothing comparable in any file cheap to read, so a cached artifact reports {@code cached} — it came
 * from one of the build's declared repositories, and {@link Repos} is what says whether those are
 * repositories anyone else could use.
 */
public final class GradleDeps {

    private GradleDeps() { }

    /** The model the dependency verbs should read. {@code allowRun} false never launches gradle. */
    public static GradleModel model(Path root, boolean allowRun, Consumer<String> log) {
        return allowRun ? Gradle.model(root, false, false, log) : StaticModel.of(root, false, log);
    }

    public static List<DepNode> of(Path root, int depth, boolean allowRun, Consumer<String> log) {
        return from(model(root, allowRun, log), depth);
    }

    public static List<DepNode> from(GradleModel model, int depth) {
        return model.deps(depth + 1).stream()
                .map(d -> new DepNode(d.coords(), Math.max(0, d.depth() - 1), origin(d.coords()))).toList();
    }

    /**
     * Gradle's cache first, then maven's. A build declaring {@code mavenLocal()} really does resolve
     * from {@code ~/.m2}, so an artifact found only there is reported the way maven reports it —
     * present, and published nowhere anyone else can reach.
     */
    public static Origin origin(Coords c) {
        if (Artifacts.inGradleCache(c)) return Origin.CACHED;
        if (Artifacts.inM2(c)) {
            var maven = Origin.of(c);
            return maven.isMissing() ? Origin.CACHED : maven;
        }
        return Origin.MISSING;
    }

    /** Does this build declare a dependency on {@code groupId:artifactId}? Reads files only. */
    public static boolean declares(Path root, String groupArtifact) {
        var modules = GradleProject.moduleRoots(root);
        var catalog = Catalog.of(root);
        return Deps.mentioned(Builds.buildFiles(root, modules), catalog).stream()
                .anyMatch(c -> (c.groupId() + ":" + c.artifactId()).equals(groupArtifact));
    }

    /** The verdict lines {@code releasable} adds for a gradle build, on top of the per-artifact origins. */
    public static List<String> repositoryFindings(Path root, List<Path> moduleRoots) {
        var declared = Repos.declared(root, moduleRoots);
        var out = new ArrayList<String>();
        if (declared.isEmpty()) {
            out.add("no repository declaration was found in the build files, so where dependencies come "
                    + "from could not be established (a convention plugin may declare them)");
            return out;
        }
        out.add("repositories declared: " + String.join(", ", declared));
        if (declared.contains(Repos.LOCAL))
            out.add("mavenLocal() is declared - anything resolved from it exists only on machines where "
                    + "somebody installed it, which is exactly the maven LOCAL-ONLY problem");
        var nonPublic = Repos.nonPublic(declared).stream().filter(d -> !Repos.LOCAL.equals(d)).toList();
        if (!nonPublic.isEmpty())
            out.add("consumers would also need these repositories configured: " + String.join(", ", nonPublic));
        return out;
    }

    public static boolean publishable(Path root, List<Path> moduleRoots, List<DepNode> deps) {
        var declared = Repos.declared(root, moduleRoots);
        return deps.stream().noneMatch(n -> n.origin().isMissing()
                || n.origin().kind() == Origin.Kind.LOCAL_ONLY)
                && !declared.contains(Repos.LOCAL)
                && Repos.nonPublic(declared).isEmpty();
    }
}
