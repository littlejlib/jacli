package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Coords;

/**
 * {@code ~/.gradle/caches/modules-2/files-2.1}, where gradle keeps downloaded artifacts one hash
 * directory deep: {@code <group>/<name>/<version>/<sha1>/<name>-<version>.jar}. The group is a single
 * dotted directory here, unlike maven's split path, so the two repositories need separate readers.
 * <p>
 * What gradle does NOT record in any file cheap to read is which repository an artifact came from.
 * Presence therefore proves the artifact was downloaded from one of the build's declared repositories
 * and no more, which is why {@code releasable} reads the declared repositories instead of guessing.
 */
public final class GradleCache {

    private GradleCache() { }

    public static final String MODULES = "modules-2", FILES = "files-2.1";

    public static Path home() {
        var env = System.getenv("GRADLE_USER_HOME");
        return env != null && !env.isBlank() ? Path.of(env)
                : Path.of(System.getProperty("user.home"), ".gradle");
    }

    public static Path modules() { return home().resolve("caches").resolve(MODULES).resolve(FILES); }

    public static boolean available() { return Files.isDirectory(modules()); }

    /** The newest matching artifact, since one version can sit under several content hashes. */
    public static Path artifact(Coords c, String ext) {
        if (c.version() == null || c.version().isBlank()) return null;
        var dir = modules().resolve(c.groupId()).resolve(c.artifactId()).resolve(c.version());
        if (!Files.isDirectory(dir)) return null;
        var wanted = c.artifactId() + "-" + c.version() + "." + ext;
        try (var hashes = Files.list(dir)) {
            return hashes.filter(Files::isDirectory).map(h -> h.resolve(wanted))
                    .filter(Files::isRegularFile).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static Path jar(Coords c) { return artifact(c, "jar"); }

    public static Path pom(Coords c) { return artifact(c, "pom"); }

    public static boolean present(Coords c) { return jar(c) != null || pom(c) != null; }

    /** Every version of an artifact the cache holds, for reporting a near miss rather than MISSING. */
    public static List<String> versionsOf(String groupId, String artifactId) {
        var dir = modules().resolve(groupId).resolve(artifactId);
        if (!Files.isDirectory(dir)) return List.of();
        try (var versions = Files.list(dir)) {
            return versions.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
