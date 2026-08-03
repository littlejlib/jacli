package littlejlib.jacli.proj;

import module java.base;

/**
 * Where the project roots are — a filesystem question, which is the one shape of question the index
 * cannot answer. Nothing here reads a pom's contents or resolves a classpath; it only looks for the
 * files that mark a directory as a project, so it is cheap enough to run before deciding anything.
 * <p>
 * It exists because two failures share this one computation. {@code scope --suggest} can only propose a
 * sibling it has ALREADY indexed and that already has a dependency edge, so a family nobody has touched
 * yet is invisible to it. And pointing a verb at the directory that HOLDS such a family used to index
 * every java file beneath it as one nameless project — a workspace of five projects became one row of
 * 1753 files that answered questions about files never meant to be compiled together.
 */
public final class Workspace {

    private Workspace() { }

    public static final int MAX_DEPTH = 4;

    /** Directories never worth walking into: build output, VCS metadata, dependency dumps. */
    public static final Set<String> SKIP = Set.of("target", "build", "out", "bin", "node_modules",
            ".git", ".svn", ".hg", ".idea", ".gradle", ".mvn", "dist");

    /** Whatever {@link Builds} recognises, so a new build system is understood here the moment it is there. */
    public static boolean isProjectRoot(Path dir) { return Builds.marked(dir); }

    /** The build files that would have counted, for a message that says what was looked for. */
    public static List<String> markers() {
        var out = new ArrayList<String>();
        out.add(Poms.POM);
        out.addAll(Builds.SETTINGS);
        out.addAll(Builds.SCRIPTS);
        return out;
    }

    public static boolean hasSourceLayout(Path dir) {
        return Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"));
    }

    /**
     * Project roots at or under {@code dir}, outermost first. A root is not descended into: a maven
     * reactor's modules belong to the reactor, which jacli indexes as one project, so listing them
     * separately would invite indexing the same sources twice under two ids.
     */
    public static List<Path> roots(Path dir, int maxDepth) {
        var found = new ArrayList<Path>();
        collect(dir, maxDepth, 0, found);
        return found;
    }

    static void collect(Path dir, int maxDepth, int depth, List<Path> found) {
        if (depth > maxDepth || !Files.isDirectory(dir)) return;
        if (isProjectRoot(dir)) {
            found.add(dir);
            return;
        }
        for (var child : children(dir)) collect(child, maxDepth, depth + 1, found);
    }

    static List<Path> children(Path dir) {
        try (var list = Files.list(dir)) {
            return list.filter(Files::isDirectory)
                    .filter(p -> !SKIP.contains(p.getFileName().toString()))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted().toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /**
     * True when {@code dir} names no project of its own but contains ones that do — the shape that used
     * to be swallowed whole. A plain directory of java files with no project inside it is NOT this, and
     * stays perfectly usable: that is a real, if unnamed, project.
     */
    public static boolean isWorkspace(Path dir) {
        return !isProjectRoot(dir) && !hasSourceLayout(dir) && !roots(dir, MAX_DEPTH).isEmpty();
    }

    /**
     * Refuses rather than indexing a workspace as one project. A refusal is right here because the
     * alternative is not a worse answer but a convincing one: the index gains a project that compiles
     * unrelated trees together, reports a tier for them, and goes on answering from it.
     */
    public static void refuseIfWorkspace(Path root) {
        if (!isWorkspace(root)) return;
        var found = roots(root, MAX_DEPTH);
        var sb = new StringBuilder(root + " holds " + found.size()
                + (found.size() == 1 ? " project" : " separate projects") + " and is not one itself.\n");
        sb.append("Indexing it would compile every java file beneath it as a single nameless project.\n");
        found.forEach(p -> sb.append("  ").append(p).append('\n'));
        sb.append("Point -p at one of them, or make them a scope:\n");
        sb.append("  jacli scope <name>");
        found.forEach(p -> sb.append(" --add ").append(p));
        sb.append('\n');
        throw new IllegalStateException(sb.toString());
    }
}
