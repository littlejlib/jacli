package littlejlib.jacli.proj;

import module java.base;
import littlejlib.jacli.core.Src;

/**
 * Change detection for incremental reindexing. A warm walk of 488 files measures around 100 ms,
 * which is why this can run on every query rather than relying only on folder watching.
 */
public final class Fingerprint {

    private Fingerprint() { }

    public static Map<String, FileStamp> of(Path root, List<Path> files) {
        var out = new LinkedHashMap<String, FileStamp>();
        for (var f : files) {
            var rel = Src.rel(root, f);
            out.put(rel, stamp(root, rel, f));
        }
        return out;
    }

    public static FileStamp stamp(Path root, String rel, Path f) {
        try {
            var a = Files.readAttributes(f, BasicFileAttributes.class);
            return new FileStamp(rel, a.size(), a.lastModifiedTime().toMillis());
        } catch (IOException e) {
            return new FileStamp(rel, -1, -1);
        }
    }

    public static String digest(Collection<FileStamp> stamps) {
        var h = 0xcbf29ce484222325L;
        for (var s : stamps.stream().sorted(Comparator.comparing(FileStamp::rel)).toList())
            for (var ch : s.line().toCharArray()) {
                h ^= ch;
                h *= 0x100000001b3L;
            }
        return Long.toHexString(h);
    }

    /** Files present now whose stamp differs from {@code known}, plus files that vanished. */
    public static Changes diff(Map<String, FileStamp> now, Map<String, FileStamp> known) {
        var changed = new ArrayList<String>();
        var removed = new ArrayList<String>();
        now.forEach((rel, s) -> {
            if (!s.sameAs(known.get(rel))) changed.add(rel);
        });
        known.keySet().forEach(rel -> {
            if (!now.containsKey(rel)) removed.add(rel);
        });
        return new Changes(changed, removed);
    }
}
