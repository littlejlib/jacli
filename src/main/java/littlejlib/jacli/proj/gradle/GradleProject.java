package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.core.Src;
import littlejlib.jacli.proj.*;

/**
 * A gradle build seen as a {@link JavaProject}, so every verb downstream is indifferent to which
 * build tool produced it.
 * <p>
 * Module roots come from the settings file rather than from a walk of the disk, which is what keeps
 * abandoned directories out: teavm still carries {@code html4j}, {@code samples/gamepad} and
 * {@code tools/eclipse} as complete source trees that the build no longer includes, and archetype
 * templates under {@code tools/maven/webapp/src/main/resources} that a glob reads as a source root
 * and a pom.
 */
public final class GradleProject {

    private GradleProject() { }

    public static final int MAX_CLIMB = 8;

    /** The directory holding the settings file, which is gradle's own definition of the build root. */
    public static Path root(Path dir) {
        var current = dir;
        for (var i = 0; i < MAX_CLIMB && current != null; i++) {
            if (Builds.settings(current) != null) return climbThroughIncludedBuilds(current);
            var parent = current.getParent();
            if (parent == null) return dir;
            current = parent;
        }
        return dir;
    }

    /** An included build has its own settings file, but the workspace root is the one that includes it. */
    static Path climbThroughIncludedBuilds(Path settingsDir) {
        var current = settingsDir;
        for (var i = 0; i < MAX_CLIMB; i++) {
            var parent = current.getParent();
            if (parent == null || Builds.settings(parent) == null) return current;
            if (!Settings.claims(parent, current)) return current;
            current = parent;
        }
        return current;
    }

    /** Cheap discovery, for callers that must not trigger a gradle run. */
    public static List<Path> moduleRoots(Path root) {
        return StaticModel.discover(root, new ArrayList<>()).entrySet().stream()
                .filter(e -> !":".equals(e.getKey())).map(Map.Entry::getValue)
                .filter(Files::isDirectory).distinct().toList();
    }

    public static List<Path> sourceRoots(Path root, List<Path> moduleRoots, boolean includeTests) {
        var bases = new ArrayList<Path>();
        bases.add(root);
        bases.addAll(moduleRoots);
        var out = new ArrayList<Path>();
        for (var b : bases) {
            out.addAll(StaticModel.mainSources(b));
            if (includeTests) out.addAll(StaticModel.testSources(b));
        }
        return out.stream().distinct().toList();
    }

    public static List<Path> outputDirs(Path root, List<Path> moduleRoots) {
        var bases = new ArrayList<Path>();
        bases.add(root);
        bases.addAll(moduleRoots);
        var out = new ArrayList<Path>();
        for (var b : bases) for (var lang : List.of("java", "kotlin"))
            out.add(b.resolve("build").resolve("classes").resolve(lang).resolve("main"));
        return out;
    }

    public static JavaProject open(Path root, boolean includeTests, boolean force, Consumer<String> log) {
        var model = Gradle.model(root, includeTests, force, log);
        var modules = model.moduleDirs();
        if (modules.isEmpty()) modules = moduleRoots(root);
        var sourceRoots = model.sourceRoots(includeTests);
        if (sourceRoots.isEmpty()) sourceRoots = sourceRoots(root, modules, includeTests);
        return new JavaProject(BuildKind.GRADLE, root, model.coords(), sourceRoots, modules,
                classpath(root, modules, model), Src.javaFiles(sourceRoots));
    }

    /**
     * Compiled output first, then the model's jars — the same order the maven side uses, and for the
     * same reason: a single-file reindex resolves every project-internal type out of the last build's
     * bytecode instead of recompiling its siblings.
     */
    static Resolved classpath(Path root, List<Path> modules, GradleModel model) {
        var notes = new ArrayList<>(model.notes());
        var parts = new ArrayList<String>();
        var declared = model.outputDirs();
        var candidates = declared.isEmpty() ? outputDirs(root, modules) : declared;
        var missing = 0;
        for (var dir : candidates) {
            if (Files.isDirectory(dir)) parts.add(dir.toString());
            else missing++;
        }
        var hasOwnClasses = !parts.isEmpty();
        var jars = model.classpath();
        if (!jars.isBlank()) parts.add(jars);
        if (missing > 0) notes.add(missing + " of " + candidates.size() + " build/classes directories do not "
                + "exist yet" + (hasOwnClasses ? "" : " (none of them do)")
                + " - run a gradle build to make single-file reindex cheap and resolution more complete");
        return new Resolved(String.join(File.pathSeparator, parts), model.source(), hasOwnClasses,
                List.copyOf(notes));
    }
}
