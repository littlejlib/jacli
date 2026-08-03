package littlejlib.jacli;

import module java.base;

/** The gradle fixture builds, resolved from test-classes when maven has copied them there. */
public final class GradleFixture {

    private GradleFixture() { }

    public static final String MAIN = "fixture-gradle", DYNAMIC = "fixture-gradle-dynamic";

    public static Path root() { return dir(MAIN); }

    public static Path dynamic() { return dir(DYNAMIC); }

    static Path dir(String name) {
        var fromTarget = Path.of("target", "test-classes", name);
        var chosen = Files.isDirectory(fromTarget) ? fromTarget : Path.of("src", "test", "resources", name);
        if (!Files.isDirectory(chosen)) throw new IllegalStateException("gradle fixture missing: " + name);
        return chosen.toAbsolutePath().normalize();
    }

    public static String slashes(Path p) { return p.toString().replace('\\', '/'); }
}
