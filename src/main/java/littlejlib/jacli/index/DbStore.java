package littlejlib.jacli.index;

import module java.base;
import com.arcadedb.database.Database;
import littlejlib.jacli.core.*;
import littlejlib.jacli.query.*;
import static xyz.jphil.arcadedb.datahelper.Query.query;

/**
 * The indexed store. Every lookup is an indexed read, which is the whole point: the javac pass that
 * produced these rows costs seconds and answering from them costs milliseconds, however many times
 * an agent asks.
 * <p>
 * Supertype rows are attached only where a verb needs them, and subtype lookups fetch their
 * declarations in one batched query rather than one per child — a deep hierarchy otherwise turns a
 * single question into hundreds of round trips.
 * <p>
 * Every read goes through {@link Safe}, so an index entry whose record has gone costs that one row and
 * a warning rather than the whole answer.
 */
public final class DbStore implements Store {

    final Database db;
    final String pid;
    final Path root;
    final Meta meta;

    public DbStore(Database db, String pid, Path root, Meta meta) {
        this.db = db;
        this.pid = pid;
        this.root = root;
        this.meta = meta;
    }

    @Override public Path root() { return root; }

    @Override public Meta meta() { return meta; }

    @Override
    public List<Decl> declsByName(String simpleName) {
        return decls(query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).eq(JDecl.$name, simpleName));
    }

    @Override
    public Optional<Decl> declByKey(String key) {
        return decls(query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).eq(JDecl.$key, key))
                .stream().findFirst();
    }

    @Override
    public List<Occurrence> refsTo(String targetKey) {
        return occurrences(query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).eq(JRef.$targetKey, targetKey));
    }

    @Override
    public List<Occurrence> refsFrom(String enclosingKey) {
        return occurrences(query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid)
                .eq(JRef.$enclosingKey, enclosingKey));
    }

    @Override
    public List<Decl> declsWithSuper(String key) { return subtypes(key, false); }

    @Override
    public List<Decl> directSubtypesOf(String key) { return subtypes(key, true); }

    @Override
    public List<String> supersOf(String key) {
        return Safe.list(query(db, JSuper.TYPEDEF).eq(JSuper.$projectId, pid).eq(JSuper.$childKey, key))
                .stream().map(JSuper::superKey).distinct().toList();
    }

    List<Decl> subtypes(String key, boolean directOnly) {
        var children = Safe.list(query(db, JSuper.TYPEDEF).eq(JSuper.$projectId, pid)
                        .eq(JSuper.$superKey, key)).stream()
                .filter(s -> !directOnly || Boolean.TRUE.equals(s.direct()))
                .map(JSuper::childKey).distinct().toList();
        return byKeys(children);
    }

    List<Decl> byKeys(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        return decls(query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).in(JDecl.$key, keys));
    }

    @Override
    public List<Decl> declsInType(String typeKey) {
        return decls(query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).eq(JDecl.$ownerKey, typeKey));
    }

    @Override
    public List<String> targetKeysByName(String simpleName) {
        return Safe.list(query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).eq(JRef.$targetName, simpleName))
                .stream().map(JRef::targetKey).distinct().toList();
    }

    @Override
    public Set<String> referencedKeys() {
        var out = new HashSet<String>();
        for (var r : Safe.list(query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid))) out.add(r.targetKey());
        return out;
    }

    /** Supertypes are attached here because {@code unused} needs them, in one query rather than per row. */
    @Override
    public Stream<Decl> allDecls() {
        var supers = new HashMap<String, List<String>>();
        for (var s : Safe.list(query(db, JSuper.TYPEDEF).eq(JSuper.$projectId, pid)))
            supers.computeIfAbsent(s.childKey(), k -> new ArrayList<>()).add(s.superKey());
        return decls(query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid)).stream()
                .map(d -> supers.containsKey(d.key()) ? Rows.withSupers(d, supers.get(d.key())) : d);
    }

    List<Decl> decls(xyz.jphil.arcadedb.datahelper.Query<JDecl> rows) {
        return Safe.list(rows).stream().map(r -> Rows.toDecl(r, root)).toList();
    }

    List<Occurrence> occurrences(xyz.jphil.arcadedb.datahelper.Query<JRef> rows) {
        return Safe.list(rows).stream().map(r -> Rows.toOccurrence(r, root)).toList();
    }
}
