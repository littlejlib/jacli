package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.index.*;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * Two threads over the hot set.
 * <p>
 * The event loop uses WatchService as a TRIGGER only — never as the truth. An event means "something
 * happened", the fingerprint sweep decides what actually changed. That split matters because
 * WatchService genuinely loses events: on overflow it hands back a single OVERFLOW and nothing else,
 * and a directory created after registration is unwatched until we notice. A fingerprint sweep, by
 * contrast, is a state comparison and cannot miss a change — it can only be late.
 * <p>
 * So the safety net is a slow poll, minutes not seconds, existing purely to cover what the watcher
 * dropped. It is cheap precisely because it is no longer the primary mechanism.
 */
public final class Watcher {

    public static final Duration SAFETY_NET = Duration.ofSeconds(45), SETTLE = Duration.ofMillis(250);

    /** A hot project nobody has asked about for this long is left alone by the safety net: its watch still reports changes. */
    static final int IDLE_MINUTES = 2;

    final ProjectIndex index;
    final Hot hot;
    final AtomicBoolean stopping = new AtomicBoolean();
    Thread events, safetyNet;

    public Watcher(ProjectIndex index, Hot hot) {
        this.index = index;
        this.hot = hot;
    }

    public void start() {
        pinnedFromIndex().forEach(root -> hot.touch(root, littlejlib.jacli.proj.Home.token(root), true));
        events = daemonThread(this::eventLoop, "jacli-watch-events");
        safetyNet = daemonThread(this::safetyNetLoop, "jacli-watch-safety");
    }

    public void stop() {
        stopping.set(true);
        if (events != null) events.interrupt();
        if (safetyNet != null) safetyNet.interrupt();
    }

    void eventLoop() {
        while (!stopping.get()) {
            WatchKey key;
            try {
                key = hot.service().take();
            } catch (InterruptedException | ClosedWatchServiceException e) {
                return;
            }
            var pid = hot.ownerOf(key);
            try {
                for (var event : key.pollEvents()) handle(event, key, pid);
            } finally {
                if (!key.reset() && pid != null) hot.markDirty(pid);
            }
            if (pid != null) refreshAfterSettling(pid);
        }
    }

    void handle(WatchEvent<?> event, WatchKey key, String pid) {
        if (pid == null) return;
        var hp = hot.get(pid);
        if (event.kind() == OVERFLOW) {
            if (hp != null) hp.watchUnreliable("the watcher dropped events");
            return;
        }
        hot.markDirty(pid);
        if (event.kind() == ENTRY_CREATE && key.watchable() instanceof Path dir
                && event.context() instanceof Path name) {
            var created = dir.resolve(name);
            if (Files.isDirectory(created)) hot.registerNew(created, pid);
        }
    }

    /**
     * Editors write a file several times in quick succession, so let the burst settle rather than
     * reindexing per keystroke. Any event arriving during the pause has already set the dirty flag.
     */
    void refreshAfterSettling(String pid) {
        try {
            Thread.sleep(SETTLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        refresh(pid);
    }

    void safetyNetLoop() {
        while (!stopping.get()) {
            try {
                Thread.sleep(SAFETY_NET);
            } catch (InterruptedException e) {
                return;
            }
            hot.all().stream().filter(hp -> hp.idleMinutes() < IDLE_MINUTES).toList().forEach(hp -> {
                hp.markDirty();
                refresh(hp.pid());
            });
        }
    }

    /** The sweep and reindex, off the query path. Failures are logged and never propagate to a caller. */
    void refresh(String pid) {
        var hp = hot.get(pid);
        if (hp == null || !hp.dirty.get()) return;
        IdleTrim.touched();
        try {
            index.ensure(hp.root(), false, false,
                    s -> System.err.println("jacli watch: " + hp.root().getFileName() + ": " + s));
            IdleTrim.touched();
        } catch (RuntimeException e) {
            System.err.println("jacli watch: " + hp.root() + ": " + e);
        }
    }

    List<Path> pinnedFromIndex() {
        try {
            return Catalog.get().all().stream().filter(p -> Boolean.TRUE.equals(p.watched()))
                    .map(JProject::root).filter(Objects::nonNull).map(Path::of)
                    .filter(Files::isDirectory).toList();
        } catch (RuntimeException e) {
            System.err.println("jacli watch: could not read pinned projects: " + e);
            return List.of();
        }
    }

    static Thread daemonThread(Runnable body, String name) {
        var t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
        return t;
    }
}
