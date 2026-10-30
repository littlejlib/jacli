package littlejlib.jacli.index;

import module java.base;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import littlejlib.jacli.proj.Home;

/**
 * Which projects are indexed, which scope each belongs to and which are pinned: one small JSON file,
 * rewritten whole and atomically. The declarations and references are not here, they are in each
 * project's own pack, so a project being rebuilt cannot disturb another.
 * <p>
 * The file is reread when it changes on disk, so a second process never works from a stale copy.
 */
public final class Catalog {

    static final ObjectMapper JSON = new ObjectMapper();
    static Catalog instance;

    final Path file = Home.file("catalog.json");
    final Map<String, JProject> rows = new LinkedHashMap<>();
    FileTime loaded;

    public static synchronized Catalog get() {
        if (instance == null) instance = new Catalog();
        return instance;
    }

    synchronized void refresh() {
        try {
            var stamp = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
            if (Objects.equals(stamp, loaded)) return;
            rows.clear();
            if (stamp != null)
                for (var row : JSON.readValue(file.toFile(), new TypeReference<List<JProject>>() { }))
                    rows.put(row.projectId(), row);
            loaded = stamp;
        } catch (IOException | RuntimeException e) {
            System.err.println("jacli: could not read " + file + " (" + e.getMessage() + "); treating it as empty");
        }
    }

    synchronized void save() {
        try {
            var tmp = Files.createTempFile(file.getParent(), "catalog", ".tmp");
            JSON.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), List.copyOf(rows.values()));
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            loaded = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized JProject project(String pid) {
        refresh();
        return rows.get(pid);
    }

    /** Newest first. */
    public synchronized List<JProject> all() {
        refresh();
        return rows.values().stream()
                .sorted(Comparator.comparing((JProject p) -> p.indexedAt() == null ? 0L : p.indexedAt()).reversed())
                .toList();
    }

    public List<JProject> inGroup(String group) {
        return all().stream().filter(p -> group.equals(p.scopeGroup())).toList();
    }

    /** The group a project belongs to, or null. Used to tell a caller that a wider scope exists. */
    public String groupOf(String pid) {
        var row = project(pid);
        return row == null || row.scopeGroup() == null || row.scopeGroup().isBlank() ? null : row.scopeGroup();
    }

    public List<String> groups() {
        return all().stream().map(JProject::scopeGroup).filter(Objects::nonNull).filter(s -> !s.isBlank())
                .distinct().sorted().toList();
    }

    /** Changes one row, creating it first when the project is new, and writes the file. */
    public synchronized JProject update(String pid, Consumer<JProject> change) {
        refresh();
        var row = rows.computeIfAbsent(pid, k -> new JProject().projectId(pid));
        change.accept(row);
        save();
        return row;
    }

    /**
     * Removes every trace of one project. The index is a cache, but a wrong entry in it is worse than a
     * missing one — a directory indexed as an accidental catch-all keeps answering questions about files
     * that were never meant to be one project.
     */
    public synchronized boolean forget(String pid) {
        refresh();
        if (rows.remove(pid) == null) return false;
        save();
        Packs.delete(pid);
        return true;
    }
}
