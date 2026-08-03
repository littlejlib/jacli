package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.proj.JavaProject;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * The set of projects the daemon is actively watching.
 * <p>
 * Membership is OBSERVED, not declared: if a query arrives about a project, that project is hot by
 * definition, so the tool marks itself rather than waiting to be told with a flag an agent will forget.
 * It is deliberately not persisted — hotness is a property of the current session's activity, and
 * re-establishing watches on restart for projects nobody has asked about would hold handles
 * speculatively. A project the user PINNED with {@code index --watch} is a stated preference and does
 * persist, in the index; it is simply exempt from eviction here.
 * <p>
 * Eviction is safe to be aggressive about because going cold is not a failure — it falls back to
 * sweeping fingerprints on the query, which is what every query did before. So the cost of evicting is
 * one sweep, never a wrong answer. That matters, because the expensive thing held per project is not
 * the watch registrations but the warm javac file manager, with an open handle per classpath jar.
 * <p>
 * Only SOURCE ROOTS are watched, never the project root. With {@code target/} inside the watch a single
 * {@code mvn compile} writes thousands of class files and overflows the watcher, which is precisely how
 * a watcher loses the events that matter.
 */
public final class Hot implements littlejlib.jacli.index.Freshness {

    public static final int MAX_HOT = 4, HARD_MAX = 16;
    public static final Duration TTL = Duration.ofMinutes(30), GRACE = Duration.ofSeconds(30);

    final WatchService service;
    final Map<String, HotProject> byPid = new ConcurrentHashMap<>();
    final Map<WatchKey, String> keyOwner = new ConcurrentHashMap<>();
    final Consumer<String> onEvict;

    public Hot(Consumer<String> onEvict) throws IOException {
        this.service = FileSystems.getDefault().newWatchService();
        this.onEvict = onEvict;
    }

    public WatchService service() { return service; }

    public Collection<HotProject> all() { return byPid.values(); }

    public HotProject get(String pid) { return byPid.get(pid); }

    /** Called on every query. Registers a watch the first time, and evicts to stay under the cap. */
    public synchronized HotProject touch(Path root, String pid, boolean pinned) {
        var hp = byPid.get(pid);
        if (hp != null) {
            hp.touch();
            return hp;
        }
        hp = new HotProject(root, pid, pinned);
        byPid.put(pid, hp);
        evictExcess();
        return hp;
    }

    /** Watches the source roots. Called once the project has been resolved, since that names them. */
    public synchronized void watch(HotProject hp, JavaProject project) {
        if (!hp.keys.isEmpty()) return;
        try {
            for (var srcRoot : project.sourceRoots()) registerTree(srcRoot, hp);
            hp.watchHealthy = !hp.keys.isEmpty();
            if (!hp.watchHealthy) hp.watchUnreliable("no source directories could be registered");
        } catch (IOException | RuntimeException e) {
            hp.watchUnreliable(String.valueOf(e.getMessage()));
        }
    }

    void registerTree(Path start, HotProject hp) throws IOException {
        if (!Files.isDirectory(start)) return;
        try (var walk = Files.walk(start)) {
            for (var dir : walk.filter(Files::isDirectory).toList()) register(dir, hp);
        }
    }

    void register(Path dir, HotProject hp) {
        try {
            var key = dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            hp.keys.add(key);
            keyOwner.put(key, hp.pid);
        } catch (IOException e) {
            hp.watchUnreliable("cannot watch " + dir.getFileName() + ": " + e.getMessage());
        }
    }

    /** A directory created after registration is not watched until we add it. */
    public void registerNew(Path dir, String pid) {
        var hp = byPid.get(pid);
        if (hp == null || !Files.isDirectory(dir)) return;
        try {
            registerTree(dir, hp);
        } catch (IOException e) {
            hp.watchUnreliable("cannot watch new directory " + dir + ": " + e.getMessage());
        }
    }

    public String ownerOf(WatchKey key) { return keyOwner.get(key); }

    /**
     * Vouches only for a project we hold a healthy watch on and have swept since its last event. A
     * project that is merely in the hot set is not enough — the watch has to be the thing that would
     * have told us about a change.
     */
    @Override
    public JavaProject verifiedProject(String pid) {
        var hp = byPid.get(pid);
        if (hp == null) return null;
        hp.touch();
        return hp.verifiedClean() ? hp.project : null;
    }

    @Override
    public void vouch(String pid, JavaProject project) {
        var hp = touch(project.root(), pid, isPinned(pid));
        watch(hp, project);
        hp.markVerified(project);
    }

    boolean isPinned(String pid) {
        var existing = byPid.get(pid);
        return existing != null && existing.pinned();
    }

    public void markDirty(String pid) {
        var hp = byPid.get(pid);
        if (hp != null) hp.markDirty();
    }

    public void markAllDirty() { byPid.values().forEach(HotProject::markDirty); }

    /**
     * Least recently used first, pinned projects exempt, and anything idle past the TTL.
     * <p>
     * A query {@code --scope}d over more projects than the cap used to evict its own working set while
     * it was still running: the members are touched one after another, so reaching member five put the
     * map over the cap and the least recently used entry was member one, milliseconds old. Every scoped
     * call then re-swept every member, defeating the daemon for the case it exists to serve. So recent
     * use is treated as use: within {@link #GRACE} a project is in flight, not idle, and the soft cap
     * yields to it. {@link #HARD_MAX} still bounds the warm javac file managers, because that grace must
     * not become a way for a session touching many projects to hold all of them open at once.
     */
    synchronized void evictExcess() {
        byPid.values().stream().filter(hp -> !hp.pinned)
                .filter(hp -> hp.idleMinutes() >= TTL.toMinutes()).toList()
                .forEach(hp -> evict(hp, "idle " + hp.idleMinutes() + " min"));
        trim(hp -> !hp.pinned && !hp.inUse(), MAX_HOT, "least recently used, over the cap of " + MAX_HOT);
        trim(hp -> !hp.pinned, HARD_MAX, "least recently used, over the hard ceiling of " + HARD_MAX);
    }

    void trim(Predicate<HotProject> evictable, int cap, String why) {
        var candidates = byPid.values().stream().filter(evictable)
                .sorted(Comparator.comparingLong(hp -> hp.lastUsed)).toList();
        var over = byPid.size() - cap;
        for (var i = 0; i < over && i < candidates.size(); i++) evict(candidates.get(i), why);
    }

    public synchronized void evict(HotProject hp, String why) {
        byPid.remove(hp.pid);
        hp.keys.forEach(k -> {
            keyOwner.remove(k);
            k.cancel();
        });
        hp.keys.clear();
        onEvict.accept(hp.pid);
        /* Routine housekeeping that by design never changes an answer, so it belongs under -v rather
         * than in the middle of every reply. */
        if (Captured.verbose) System.err.println("jacli watch: released " + hp.root.getFileName() + " (" + why + ")");
    }

    public synchronized void closeAll() {
        List.copyOf(byPid.values()).forEach(hp -> evict(hp, "daemon stopping"));
        try {
            service.close();
        } catch (IOException e) {
            // the process is going away regardless
        }
    }
}
