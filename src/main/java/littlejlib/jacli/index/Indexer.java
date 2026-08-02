package littlejlib.jacli.index;

import module java.base;
import com.arcadedb.database.Database;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.*;

/**
 * Writes an analysis into the index. Two modes: a whole project, and a set of changed files.
 * <p>
 * Rows are keyed by project so one reactor can be replaced without touching another, and writes are
 * chunked into transactions — a 488-file project produces about 13000 reference rows and one
 * transaction that size is a needlessly large unit of work to lose.
 */
public final class Indexer {

    private Indexer() { }

    public static final int BATCH = 2000;

    public static void indexAll(Database db, JavaProject p, Analysis a, long indexMs, Map<String, FileStamp> stamps) {
        var pid = p.id();
        deleteProjectRows(db, pid);
        writeDecls(db, pid, a.decls());
        writeSupers(db, pid, a.supers());
        writeRefs(db, pid, a.refs());
        writeStamps(db, pid, stamps.values());
        writeProject(db, pid, p, a, indexMs, stamps.size());
    }

    /** Replace only the rows belonging to {@code rels}; used after a file edit. */
    public static void indexFiles(Database db, JavaProject p, Analysis a, Collection<String> rels,
                                  Collection<String> removed, Map<String, FileStamp> stamps) {
        var pid = p.id();
        db.transaction(() -> {
            for (var rel : rels) deleteFileRows(db, pid, rel);
            for (var rel : removed) {
                deleteFileRows(db, pid, rel);
                db.command("sql", "DELETE FROM JFile WHERE projectId = ? AND rel = ?", pid, rel);
            }
        });
        writeDecls(db, pid, a.decls());
        writeSupers(db, pid, a.supers());
        writeRefs(db, pid, a.refs());
        writeStamps(db, pid, rels.stream().map(stamps::get).filter(Objects::nonNull).toList());
        touchProject(db, pid, stamps.size());
    }

    static void deleteFileRows(Database db, String pid, String rel) {
        db.command("sql", "DELETE FROM JRef WHERE projectId = ? AND file = ?", pid, rel);
        db.command("sql", "DELETE FROM JSuper WHERE projectId = ? AND childKey IN "
                + "(SELECT key FROM JDecl WHERE projectId = ? AND file = ?)", pid, pid, rel);
        db.command("sql", "DELETE FROM JDecl WHERE projectId = ? AND file = ?", pid, rel);
    }

    public static void deleteProjectRows(Database db, String pid) {
        db.transaction(() -> {
            for (var type : List.of("JRef", "JDecl", "JSuper", "JFile"))
                db.command("sql", "DELETE FROM " + type + " WHERE projectId = ?", pid);
        });
    }

    /**
     * Plain inserts, not upserts. Both callers delete the rows they are about to write first, so an
     * upsert would only add a select per row — and there are thousands of rows.
     */
    static void writeDecls(Database db, String pid, List<Decl> decls) {
        batched(db, decls, d -> new JDecl().declId(JDecl.id(pid, d.key(), d.file(), d.line())).projectId(pid)
                .key(d.key()).name(d.name()).kind(d.kind().name()).ownerKey(d.ownerKey()).file(d.file())
                .line(d.line()).col(d.col()).endLine(d.endLine()).signature(d.signature())
                .modifiers(d.modifiers()).returnType(d.returnType())
                .params(String.join(",", d.paramTypes())).doc(d.doc()).implicit(d.implicit())
                .in(db).insert());
    }

    static void writeSupers(Database db, String pid, List<SuperEdge> supers) {
        var written = new HashSet<String>();
        batched(db, supers, e -> {
            var id = JSuper.id(pid, e.childKey(), e.superKey());
            if (written.add(id)) new JSuper().superId(id).projectId(pid).childKey(e.childKey())
                    .superKey(e.superKey()).direct(e.direct()).in(db).insert();
        });
    }

    static void writeRefs(Database db, String pid, List<Occurrence> refs) {
        batched(db, refs, o -> new JRef().projectId(pid).targetKey(o.targetKey())
                .targetName(SymbolKey.simpleNameOf(Keys.spelling(o.targetKey())))
                .enclosingKey(o.enclosingKey()).file(o.file()).line(o.line()).col(o.col())
                .kind(o.kind().name()).in(db).insert());
    }

    static void writeStamps(Database db, String pid, Collection<FileStamp> stamps) {
        batched(db, List.copyOf(stamps), s -> new JFile().fileId(JFile.id(pid, s.rel())).projectId(pid)
                .rel(s.rel()).size(s.size()).mtime(s.mtime())
                .in(db).whereEq(JFile.$fileId, JFile.id(pid, s.rel())).upsert());
    }

    static void writeProject(Database db, String pid, JavaProject p, Analysis a, long indexMs, int fileCount) {
        db.transaction(() -> new JProject().projectId(pid).root(p.root().toString())
                .coords(p.coords().toString()).tier(a.tier().name()).classpathSource(p.classpath().source())
                .indexedAt(System.currentTimeMillis()).indexMs(indexMs).files(fileCount)
                .declCount(a.decls().size()).refCount(a.refs().size()).javacErrors(a.errors())
                .unresolved(a.unresolved()).watched(Boolean.FALSE).formatVersion(JProject.FORMAT)
                .notes(String.join(" | ", p.classpath().notes()))
                .in(db).whereEq(JProject.$projectId, pid).upsert());
    }

    static void touchProject(Database db, String pid, int fileCount) {
        db.transaction(() -> Counts.recount(db, pid, fileCount));
    }

    static <T> void batched(Database db, List<T> items, Consumer<T> write) {
        for (var from = 0; from < items.size(); from += BATCH) {
            var chunk = items.subList(from, Math.min(items.size(), from + BATCH));
            db.transaction(() -> chunk.forEach(write));
        }
    }
}
