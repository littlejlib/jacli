package littlejlib.jacli.proj;

import module java.base;

/** {@code ~/littlejlib/jacli} — per-organisation grouping, not a dotfile, per project convention. */
public final class Home {

    private Home() { }

    public static final String ORG = "littlejlib", APP = "jacli";

    public static Path dir() {
        return Path.of(System.getProperty("user.home"), ORG, APP);
    }

    /** A directory under the home, created. */
    public static Path dir(String... parts) {
        var p = dir();
        for (var s : parts) p = p.resolve(s);
        return mkdirs(p);
    }

    /** A file under the home, with its parent directory created. */
    public static Path file(String... parts) {
        var p = dir();
        for (var s : parts) p = p.resolve(s);
        mkdirs(p.getParent());
        return p;
    }

    static Path mkdirs(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A filesystem-safe, collision-resistant token for an absolute path. */
    public static String token(Path path) {
        var abs = path.toAbsolutePath().normalize();
        var name = abs.getFileName();
        return (name == null ? "root" : name.toString()).replaceAll("[^A-Za-z0-9_.-]", "_")
                + "-" + Integer.toHexString(abs.toString().toLowerCase(Locale.ROOT).hashCode());
    }
}
