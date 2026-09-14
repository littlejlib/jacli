package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.Cwd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Relative paths belong to the CALLER's directory, not the daemon's.
 * <p>
 * {@code Path.toAbsolutePath()} resolves against the JVM's own default directory, and inside the
 * resident daemon that is wherever the daemon was started. So {@code jacli scope luvml --add luvml},
 * run from the luvml workspace, produced {@code .../konsoletyper/teavm/usercodeluvmlluvml} — the
 * daemon's cwd with the argument glued on — and then reported it "not indexed yet", sending the
 * reader to look for a directory that had never existed. Every user-supplied path goes through
 * {@link Cwd#resolve} for this reason.
 */
class CwdTest {

    final Path original = Cwd.get();

    @AfterEach
    void restore() { Cwd.set(original); }

    @Test
    @DisplayName("a relative path resolves against the caller's directory, not the JVM's")
    void relativeUsesCallerCwd() {
        var caller = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Cwd.set(caller);
        assertEquals(caller.resolve("luvml"), Cwd.resolve(Path.of("luvml")));
    }

    @Test
    @DisplayName("the JVM default is NOT used, which is the whole defect")
    void jvmDefaultIsNotConsulted() {
        var caller = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Cwd.set(caller);
        var viaJvm = Path.of("luvml").toAbsolutePath().normalize();
        var viaCwd = Cwd.resolve(Path.of("luvml"));
        assertNotEquals(viaJvm, viaCwd, "resolution must not fall back to user.dir");
    }

    @Test
    @DisplayName("an absolute path is left alone")
    void absoluteIsUntouched() {
        Cwd.set(Path.of(System.getProperty("java.io.tmpdir")));
        var abs = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        assertEquals(abs, Cwd.resolve(abs));
    }

    @Test
    @DisplayName("dot and dot-dot are normalised away rather than carried into a stored root")
    void normalises() {
        var caller = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Cwd.set(caller);
        assertEquals(caller, Cwd.resolve(Path.of(".")));
        assertEquals(caller.getParent(), Cwd.resolve(Path.of("..")));
        assertEquals(caller.resolve("b"), Cwd.resolve(Path.of("a/../b")));
    }

    @Test
    @DisplayName("null in, null out, so an unset option needs no guard at the call site")
    void nullPassesThrough() {
        assertNull(Cwd.resolve(null));
    }
}
