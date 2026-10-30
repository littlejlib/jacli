package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.query.*;
import static littlejlib.jacli.pack.Layout.*;

/// {@link Store} over one {@link Pack}. Answers the way the database store did, so the verbs cannot
/// tell them apart: a declaration read by name or key carries no supertypes, only {@link #allDecls} does.
public final class PackStore implements Store {

    final Pack pack;
    final Path root;
    final Meta meta;

    public PackStore(Pack pack, Path root, Meta meta) {
        this.pack = pack;
        this.root = root;
        this.meta = meta;
    }

    @Override public Path root() { return root; }

    @Override public Meta meta() { return meta; }

    List<Decl> decls(List<Integer> rows) { return rows.stream().map(r -> pack.decl(r, root)).toList(); }

    @Override public List<Decl> declsByName(String name) {
        return decls(pack.rowsWhere(DECL_BY_NAME, DECLS, DECL, D_NAME, name));
    }

    @Override public Optional<Decl> declByKey(String key) {
        return decls(pack.rowsWhere(DECL_BY_KEY, DECLS, DECL, D_KEY, key)).stream().findFirst();
    }

    @Override public List<Decl> declsInType(String typeKey) {
        return decls(pack.rowsWhere(DECL_BY_OWNER, DECLS, DECL, D_OWNER, typeKey));
    }

    @Override public List<Occurrence> refsTo(String targetKey) {
        var r = pack.refRange(targetKey);
        var out = new ArrayList<Occurrence>(r[1] - r[0]);
        for (var i = r[0]; i < r[1]; i++) out.add(pack.ref(i, root));
        return out;
    }

    @Override public int refCount(String targetKey) {
        var r = pack.refRange(targetKey);
        return r[1] - r[0];
    }

    @Override public List<Occurrence> refsFrom(String enclosingKey) {
        return pack.rowsWhere(REF_BY_ENCLOSING, REFS, REF, R_ENCLOSING, enclosingKey).stream()
                .map(r -> pack.ref(r, root)).toList();
    }

    /** Edges whose supertype is {@code key}; the table is ordered by it, so they are one range. */
    List<String> childrenOf(String key, boolean directOnly) {
        var id = pack.find(key);
        if (id < 0) return List.of();
        var r = Pack.range(pack.count(SUPERS, SUPER), i -> pack.at(SUPERS, i * SUPER + 1), id);
        var out = new LinkedHashSet<String>();
        for (var i = r[0]; i < r[1]; i++)
            if (!directOnly || pack.at(SUPERS, i * SUPER + 2) == 1) out.add(pack.str(pack.at(SUPERS, i * SUPER)));
        return List.copyOf(out);
    }

    List<Decl> byKeys(List<String> keys) {
        return keys.stream().flatMap(k -> decls(pack.rowsWhere(DECL_BY_KEY, DECLS, DECL, D_KEY, k)).stream()).toList();
    }

    @Override public List<Decl> declsWithSuper(String key) { return byKeys(childrenOf(key, false)); }

    @Override public List<Decl> directSubtypesOf(String key) { return byKeys(childrenOf(key, true)); }

    @Override public List<String> supersOf(String key) {
        var id = pack.find(key);
        if (id < 0) return List.of();
        var r = Pack.range(pack.len[SUPER_BY_CHILD], i -> pack.at(SUPERS, pack.at(SUPER_BY_CHILD, i) * SUPER), id);
        var out = new LinkedHashSet<String>();
        for (var i = r[0]; i < r[1]; i++) out.add(pack.str(pack.at(SUPERS, pack.at(SUPER_BY_CHILD, i) * SUPER + 1)));
        return List.copyOf(out);
    }

    @Override public List<String> targetKeysByName(String simpleName) {
        var id = pack.find(simpleName);
        if (id < 0) return List.of();
        var r = Pack.range(pack.len[TARGET_NAMES] / 2, p -> pack.at(TARGET_NAMES, 2 * p), id);
        var out = new ArrayList<String>();
        for (var p = r[0]; p < r[1]; p++) out.add(pack.str(pack.at(TARGET_NAMES, 2 * p + 1)));
        return out;
    }

    @Override public Set<String> referencedKeys() {
        var out = new HashSet<String>();
        for (var p = 0; p < pack.len[REF_TARGETS] / 2; p++) out.add(pack.str(pack.at(REF_TARGETS, 2 * p)));
        return out;
    }

    static Decl withSupers(Decl d, List<String> superKeys) {
        return new Decl(d.key(), d.kind(), d.name(), d.ownerKey(), d.file(), d.line(), d.col(), d.endLine(),
                d.signature(), d.modifiers(), d.returnType(), d.paramTypes(), superKeys, d.doc(), d.implicit());
    }

    @Override public Stream<Decl> allDecls() {
        return IntStream.range(0, pack.declCount()).mapToObj(r -> {
            var d = pack.decl(r, root);
            var supers = supersOf(d.key());
            return supers.isEmpty() ? d : withSupers(d, supers);
        });
    }
}
