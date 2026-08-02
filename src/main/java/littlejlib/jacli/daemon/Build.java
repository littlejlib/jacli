package littlejlib.jacli.daemon;

import module java.base;

/**
 * The build this code came from, stamped in at package time.
 * <p>
 * A rebuilt jar under a still-running daemon is the single most common failure of this shape in this
 * toolchain: it surfaces as LinkageError, NoClassDefFoundError, or an answer computed by code you
 * have already replaced. So the client sends the stamp it expects and the daemon restarts itself
 * rather than serve a stale one.
 */
public final class Build {

    private Build() { }

    public static final String STAMP = load();

    static String load() {
        try (var in = Build.class.getResourceAsStream("/littlejlib/jacli/build-stamp.txt")) {
            if (in == null) return "unknown";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "unknown";
        }
    }
}
