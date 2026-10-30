package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.FileStamp;

/// What an incremental pass does to a pack: drop every row of the files that were read again, add what
/// the new pass found, keep the rest. Rewriting the whole file costs milliseconds at this size, which
/// is why a pack needs no in-place update and no delete that could leave an entry behind.
public final class PackMerge {

    private PackMerge() { }

    /**
     * @param redone files analysed again, changed or merely referencing something that changed
     * @param removed files that no longer exist
     * @param now every file's current stamp; only the redone files' are taken from it
     */
    public static PackData apply(PackData old, Set<String> redone, Collection<String> removed, Analysis found,
                                 Map<String, FileStamp> now) {
        var gone = new HashSet<>(redone);
        gone.addAll(removed);
        var goneKeys = old.decls().stream().filter(d -> gone.contains(d.file())).map(Decl::key)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        var decls = new ArrayList<>(old.decls().stream().filter(d -> !gone.contains(d.file())).toList());
        decls.addAll(found.decls());
        var refs = new ArrayList<>(old.refs().stream().filter(r -> !gone.contains(r.file())).toList());
        refs.addAll(found.refs());
        var supers = new ArrayList<>(old.supers().stream().filter(e -> !goneKeys.contains(e.childKey())).toList());
        supers.addAll(found.supers());
        var stamps = new ArrayList<>(old.stamps().stream().filter(s -> !gone.contains(s.rel())).toList());
        redone.stream().map(now::get).filter(Objects::nonNull).forEach(stamps::add);
        var m = old.meta();
        var meta = new PackMeta(m.tier(), m.root(), m.coords(), m.classpathSource(), m.notes(), stamps.size(),
                decls.size(), refs.size(), m.javacErrors(), m.unresolved(), m.formatVersion(), m.indexMs(),
                System.currentTimeMillis());
        return new PackData(meta, decls, refs, supers, stamps);
    }
}
