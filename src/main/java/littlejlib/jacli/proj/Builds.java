package littlejlib.jacli.proj;

import module java.base;

/**
 * Which build system owns a directory, and which files decide its classpath.
 * <p>
 * A directory carrying both a pom and a gradle build resolves to MAVEN unless told otherwise, so
 * every project already in an index keeps answering exactly as it did. {@code --build gradle} says
 * otherwise, and {@code status} reports when the choice was contested.
 */
public final class Builds {

    private Builds() { }

    public static final List<String>
            SETTINGS = List.of("settings.gradle.kts", "settings.gradle"),
            SCRIPTS = List.of("build.gradle.kts", "build.gradle"),
            EXTRA = List.of("gradle.properties", "gradle/libs.versions.toml");

    private static volatile BuildKind preferred;

    /** Set from {@code --build}. Requests are serialised by the daemon, so a static is enough. */
    public static void prefer(BuildKind kind) { preferred = kind; }

    public static BuildKind preferred() { return preferred; }

    public static Path first(Path dir, List<String> names) {
        for (var n : names) {
            var p = dir.resolve(n);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    public static Path settings(Path dir) { return first(dir, SETTINGS); }

    public static Path script(Path dir) { return first(dir, SCRIPTS); }

    public static boolean hasMaven(Path dir) { return Files.isRegularFile(dir.resolve(Poms.POM)); }

    public static boolean hasGradle(Path dir) { return settings(dir) != null || script(dir) != null; }

    public static boolean marked(Path dir) { return hasMaven(dir) || hasGradle(dir); }

    public static boolean contested(Path dir) { return hasMaven(dir) && hasGradle(dir); }

    public static BuildKind kindOf(Path dir) {
        var want = preferred;
        if (want == BuildKind.GRADLE && hasGradle(dir)) return BuildKind.GRADLE;
        if (want == BuildKind.MAVEN && hasMaven(dir)) return BuildKind.MAVEN;
        if (hasMaven(dir)) return BuildKind.MAVEN;
        return hasGradle(dir) ? BuildKind.GRADLE : BuildKind.PLAIN;
    }

    /** Everything whose content can change the classpath — what the classpath cache is stamped against. */
    public static List<Path> buildFiles(Path root, List<Path> moduleRoots) {
        if (kindOf(root) == BuildKind.MAVEN) return Classpath.poms(root, moduleRoots);
        var out = new ArrayList<Path>();
        var bases = new ArrayList<Path>();
        bases.add(root);
        bases.addAll(moduleRoots);
        for (var b : bases) {
            add(out, settings(b));
            add(out, script(b));
            for (var e : EXTRA) add(out, b.resolve(e));
        }
        return out;
    }

    static void add(List<Path> out, Path p) {
        if (p != null && Files.isRegularFile(p) && !out.contains(p)) out.add(p);
    }
}
