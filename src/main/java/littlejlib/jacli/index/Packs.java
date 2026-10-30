package littlejlib.jacli.index;

import module java.base;
import littlejlib.jacli.pack.Pack;
import littlejlib.jacli.proj.Home;

/**
 * The pack files, one per project, and the few that are held in memory right now. A pack is replaced by
 * writing the new one beside it and renaming, so a reader holding the old bytes is never disturbed and a
 * crash leaves the previous index, not half of a new one.
 */
public final class Packs {

    private Packs() { }

    /** Enough for the projects a session moves between; each costs about what its file does. */
    static final int KEEP = 6;

    record Held(Pack pack, long size, FileTime mtime) { }

    static final Map<String, Held> held = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Held> eldest) { return size() > KEEP; }
    };

    static Path file(String pid) { return Home.dir("packs").resolve(pid.replaceAll("[^A-Za-z0-9._-]", "_") + ".pack"); }

    public static synchronized Optional<Pack> read(String pid) {
        var file = file(pid);
        try {
            if (!Files.exists(file)) return Optional.empty();
            var size = Files.size(file);
            var mtime = Files.getLastModifiedTime(file);
            var h = held.get(pid);
            if (h != null && h.size == size && h.mtime.equals(mtime)) return Optional.of(h.pack);
            var pack = Pack.of(Files.readAllBytes(file));
            held.put(pid, new Held(pack, size, mtime));
            return Optional.of(pack);
        } catch (IOException | RuntimeException e) {
            System.err.println("jacli: " + file.getFileName() + " is unreadable (" + e.getMessage() + "), so it will be rebuilt");
            delete(pid);
            return Optional.empty();
        }
    }

    public static synchronized void write(String pid, byte[] bytes) {
        var file = file(pid);
        try {
            var tmp = Files.createTempFile(file.getParent(), "pack", ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            held.remove(pid);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static synchronized void delete(String pid) {
        held.remove(pid);
        try {
            Files.deleteIfExists(file(pid));
        } catch (IOException e) {
            System.err.println("jacli: could not delete " + file(pid) + ": " + e.getMessage());
        }
    }

    /** Lets go of what is held, for a daemon that has been idle. The files stay. */
    public static synchronized void release() { held.clear(); }

    public static synchronized void release(String pid) { held.remove(pid); }
}
