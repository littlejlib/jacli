package littlejlib.jacli.proj;

import module java.base;

/**
 * The working directory a request came from.
 * <p>
 * Needed because the daemon serves clients from other directories, and setting the {@code user.dir}
 * property does not help: the default filesystem caches its default directory when it is created, so
 * a later change to that property is silently ignored. The cwd has to be carried explicitly.
 */
public final class Cwd {

    private Cwd() { }

    private static volatile Path value = Path.of("").toAbsolutePath().normalize();

    public static Path get() { return value; }

    public static void set(Path p) { value = p.toAbsolutePath().normalize(); }

    /**
     * Resolve a path the caller typed. EVERY user-supplied path must come through here rather than
     * through {@code toAbsolutePath()}, which resolves against the JVM's own default directory — and
     * inside the daemon that is wherever the daemon happened to be started, not where the caller is.
     * <p>
     * That mismatch is not theoretical. {@code jacli scope luvml --add luvml} run from the luvml
     * workspace produced {@code C:/user/github-mirror/konsoletyper/teavm/usercodeluvmlluvml} — the
     * daemon's cwd, with the argument glued on — and reported it as "not indexed yet", sending the
     * reader to look for a directory that had never existed.
     */
    public static Path resolve(Path p) {
        return p == null ? null : p.isAbsolute() ? p.normalize() : value.resolve(p).normalize();
    }
}
