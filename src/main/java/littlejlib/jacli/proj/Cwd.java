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
}
