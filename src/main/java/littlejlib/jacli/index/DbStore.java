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
        return query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).eq(JDecl.$name, simpleName)
                .stream().map(r -> Rows.toDecl(r, root)).toList();
    }

    @Override
    public Optional<Decl> declByKey(String key) {
        var row = query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).eq(JDecl.$key, key).firstOrNull();
        return row == null ? Optional.empty() : Optional.of(Rows.toDecl(row, root));
    }

    @Override
    public List<Occurrence> refsTo(String targetKey) {
        return query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).eq(JRef.$targetKey, targetKey)
                .stream().map(r -> Rows.toOccurrence(r, root)).toList();
    }

    @Override
    public List<Occurrence> refsFrom(String enclosingKey) {
        return query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).eq(JRef.$enclosingKey, enclosingKey)
                .stream().map(r -> Rows.toOccurrence(r, root)).toList();
    }

    @Override
    public List<Decl> declsWithSuper(String key) { return subtypes(key, false); }

    @Override
    public List<Decl> directSubtypesOf(String key) { return subtypes(key, true); }

    @Override
    public List<String> supersOf(String key) {
        return query(db, JSuper.TYPEDEF).eq(JSuper.$projectId, pid).eq(JSuper.$childKey, key)
                .stream().map(JSuper::superKey).distinct().toList();
    }

    List<Decl> subtypes(String key, boolean directOnly) {
        var children = query(db, JSuper.TYPEDEF).eq(JSuper.$projectId, pid).eq(JSuper.$superKey, key)
                .stream().filter(s -> !directOnly || Boolean.TRUE.equals(s.direct()))
                .map(JSuper::childKey).distinct().toList();
        return byKeys(children);
    }

    List<Decl> byKeys(List<String> keys) {
        if (keys.isEmpty()) return List.of();
        return query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).in(JDecl.$key, keys)
                .stream().map(r -> Rows.toDecl(r, root)).toList();
    }

    @Override
    public List<Decl> declsInType(String typeKey) {
        return query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).eq(JDecl.$ownerKey, typeKey)
                .stream().map(r -> Rows.toDecl(r, root)).toList();
    }

    @Override
    public List<String> targetKeysByName(String simpleName) {
        return query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).eq(JRef.$targetName, simpleName)
                .stream().map(JRef::targetKey).distinct().toList();
    }

    @Override
    public Set<String> referencedKeys() {
        var out = new HashSet<String>();
        for (var r : query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid)) out.add(r.targetKey());
        return out;
    }

    /** Supertypes are attached here because {@code unused} needs them, in one query rather than per row. */
    @Override
    public Stream<Decl> allDecls() {
        var supers = new HashMap<String, List<String>>();
        for (var s : query(db, JSuper.TYPEDEF).eq(JSuper.$projectId, pid))
            supers.computeIfAbsent(s.childKey(), k -> new ArrayList<>()).add(s.superKey());
        return query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).stream().map(r -> Rows.toDecl(r, root))
                .map(d -> supers.containsKey(d.key()) ? Rows.withSupers(d, supers.get(d.key())) : d);
    }
}
