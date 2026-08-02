package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.query.*;

/**
 * The fixture project, analysed once into an in-memory store.
 * <p>
 * Deliberately bypasses project and classpath resolution: the fixture has no dependencies, so javac
 * resolves it fully against the platform alone. That keeps these tests offline, free of maven, and
 * deterministic — they pin the SEMANTICS of the engine rather than the state of this machine.
 */
public final class Fixture {

    private Fixture() { }

    static Store cached;

    public static synchronized Store store() {
        if (cached == null) cached = build();
        return cached;
    }

    public static Path root() {
        var fromTarget = Path.of("target", "test-classes", "fixture");
        if (Files.isDirectory(fromTarget)) return fromTarget.toAbsolutePath().normalize();
        return Path.of("src", "test", "resources", "fixture").toAbsolutePath().normalize();
    }

    static Store build() {
        var root = root();
        var files = Src.javaFiles(List.of(root));
        if (files.isEmpty()) throw new IllegalStateException("fixture sources not found under " + root);
        try (var analyzer = new Analyzer(root, "", List.of())) {
            var a = analyzer.analyze(files);
            if (a.tier() != Tier.RESOLVED) throw new IllegalStateException(
                    "fixture did not resolve cleanly (" + a.tier() + "): " + a.errorLines());
            var meta = new Meta("fx:fixture:1", root, a.tier(), a.fileCount(), a.decls().size(),
                    a.refs().size(), a.errors(), a.unresolved(), "none", true, "", List.of());
            return new MemStore(root, meta, a.decls(), a.refs(), a.supers());
        }
    }

    public static String text(Answer answer) { return answer.text(); }
}
