package littlejlib.jacli.core;

import module java.base;

/**
 * Says what is happening while it is still happening.
 * <p>
 * The first call in an unindexed project resolves a build classpath and runs javac over every file:
 * measured at 31 seconds on a 175-file project, against 0.55 for every call after it. Until now that
 * was 31 seconds of complete silence, because the daemon computes the whole reply and sends it in one
 * frame at the end. A caller cannot tell silence from a hang, and the documentation's answer — a note
 * that it "looks like a hang but is not" — is prose defending against a defect rather than a fix.
 * <p>
 * So the cost is announced before it is incurred, not after. The wait is unchanged; what changes is
 * that it is explained, which is the part that decides whether anyone waits.
 * <p>
 * Notes go to stderr, never stdout: they are progress, not answer, and a caller piping stdout into
 * something must not receive them. The sink is process-global for the same reason
 * {@code Captured.verbose} is — the daemon holds a lock for the whole request.
 */
public final class Progress {

    private Progress() { }

    private static volatile Consumer<String> sink;

    /** Installed by the daemon for the life of one request; cleared in a finally. */
    public static void sink(Consumer<String> s) { sink = s; }

    /**
     * Report something the caller is waiting on. Delivered immediately where a route exists for it,
     * and dropped rather than buffered where none does — a progress note that arrives with the answer
     * it was meant to precede is worse than no note, because it is pure noise by then.
     */
    public static void note(String text) {
        var s = sink;
        if (s != null) s.accept(text);
    }
}
