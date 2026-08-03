package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.proj.JavaProject;

/**
 * A project the daemon is currently keeping warm. Mutable on purpose: it is per-project state owned by
 * one manager, not a value passed around.
 * <p>
 * {@code verifiedAt} is what lets a query skip its fingerprint sweep. It is only meaningful while
 * {@code watchHealthy} holds — if registration failed or the watcher reported an overflow, the sweep
 * comes back, because the shortcut must never be the reason an answer is wrong.
 */
public final class HotProject {

    final Path root;
    final String pid;
    final boolean pinned;
    final List<WatchKey> keys = new ArrayList<>();
    final AtomicBoolean dirty = new AtomicBoolean(true);
    volatile long lastUsed = System.currentTimeMillis();
    volatile long verifiedAt;
    volatile boolean watchHealthy;
    volatile JavaProject project;

    HotProject(Path root, String pid, boolean pinned) {
        this.root = root;
        this.pid = pid;
        this.pinned = pinned;
    }

    public Path root() { return root; }

    public String pid() { return pid; }

    public boolean pinned() { return pinned; }

    void touch() { lastUsed = System.currentTimeMillis(); }

    /** Safe to answer without re-walking the tree: watched, healthy, and swept since the last event. */
    boolean verifiedClean() {
        return watchHealthy && !dirty.get() && verifiedAt > 0 && project != null;
    }

    void markVerified(JavaProject p) {
        project = p;
        verifiedAt = System.currentTimeMillis();
        dirty.set(false);
    }

    void markDirty() { dirty.set(true); }

    /** Registration failed or events were dropped; fall back to sweeping on every query. */
    void watchUnreliable(String why) {
        watchHealthy = false;
        dirty.set(true);
        System.err.println("jacli watch: " + root.getFileName() + " falling back to per-query sweep (" + why + ")");
    }

    long idleMinutes() {
        return Duration.ofMillis(System.currentTimeMillis() - lastUsed).toMinutes();
    }

    /** Touched so recently that a query is probably still walking its scope; not a candidate for eviction. */
    boolean inUse() {
        return System.currentTimeMillis() - lastUsed < Hot.GRACE.toMillis();
    }

    String describe() {
        return (pinned ? "pinned " : "hot ") + root + "  " + keys.size() + " dirs watched, "
                + (watchHealthy ? verifiedClean() ? "verified clean" : "dirty" : "WATCH UNRELIABLE")
                + ", idle " + idleMinutes() + " min";
    }
}
