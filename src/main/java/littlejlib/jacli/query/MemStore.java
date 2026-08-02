package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/** In-memory store for {@code --direct} runs and for verifying verbs without the daemon in the way. */
public final class MemStore implements Store {

    final Path root;
    final Meta meta;
    final List<Decl> decls;
    final Map<String, List<Decl>> byName = new HashMap<>();
    final Map<String, Decl> byKey = new HashMap<>();
    final Map<String, List<Occurrence>> byTarget = new HashMap<>();
    final Map<String, List<Occurrence>> byEnclosing = new HashMap<>();
    final Map<String, List<Decl>> bySuper = new HashMap<>();
    final Map<String, List<Decl>> byDirectSuper = new HashMap<>();
    final Map<String, List<Decl>> byOwner = new HashMap<>();
    final Map<String, Set<String>> targetsByName = new HashMap<>();
    final Map<String, List<String>> supersByChild = new HashMap<>();

    /**
     * File paths are absolutised on the way in, so the query layer is uniformly absolute whichever store
     * it is talking to. The engine emits paths relative to the project root because the index stores them
     * that way; having one store hand out relative paths and the other absolute is a trap, and it caught
     * one already.
     */
    public MemStore(Path root, Meta meta, List<Decl> decls, List<Occurrence> refs, List<SuperEdge> supers) {
        this.root = root;
        this.meta = meta;
        this.decls = decls.stream().map(d -> absolutise(root, d)).toList();
        for (var d : this.decls) {
            byName.computeIfAbsent(d.name(), k -> new ArrayList<>()).add(d);
            byKey.putIfAbsent(d.key(), d);
            if (d.ownerKey() != null) byOwner.computeIfAbsent(d.ownerKey(), k -> new ArrayList<>()).add(d);
        }
        for (var e : supers) {
            var child = byKey.get(e.childKey());
            if (child == null) continue;
            bySuper.computeIfAbsent(e.superKey(), k -> new ArrayList<>()).add(child);
            if (e.direct()) byDirectSuper.computeIfAbsent(e.superKey(), k -> new ArrayList<>()).add(child);
            supersByChild.computeIfAbsent(e.childKey(), k -> new ArrayList<>()).add(e.superKey());
        }
        for (var raw : refs) {
            var r = absolutise(root, raw);
            byTarget.computeIfAbsent(r.targetKey(), k -> new ArrayList<>()).add(r);
            if (r.enclosingKey() != null)
                byEnclosing.computeIfAbsent(r.enclosingKey(), k -> new ArrayList<>()).add(r);
            targetsByName.computeIfAbsent(SymbolKey.simpleNameOf(Keys.spelling(r.targetKey())),
                    k -> new LinkedHashSet<>()).add(r.targetKey());
        }
    }

    static Decl absolutise(Path root, Decl d) {
        return new Decl(d.key(), d.kind(), d.name(), d.ownerKey(), Src.abs(root, d.file()).toString(),
                d.line(), d.col(), d.endLine(), d.signature(), d.modifiers(), d.returnType(),
                d.paramTypes(), d.superKeys(), d.doc(), d.implicit());
    }

    static Occurrence absolutise(Path root, Occurrence o) {
        return new Occurrence(o.targetKey(), Src.abs(root, o.file()).toString(), o.line(), o.col(),
                o.kind(), o.enclosingKey());
    }

    @Override public Path root() { return root; }

    @Override public Meta meta() { return meta; }

    @Override public List<Decl> declsByName(String simpleName) {
        return byName.getOrDefault(simpleName, List.of());
    }

    @Override public Optional<Decl> declByKey(String key) { return Optional.ofNullable(byKey.get(key)); }

    @Override public List<Occurrence> refsTo(String targetKey) {
        return byTarget.getOrDefault(targetKey, List.of());
    }

    @Override public List<Occurrence> refsFrom(String enclosingKey) {
        return byEnclosing.getOrDefault(enclosingKey, List.of());
    }

    @Override public List<Decl> declsWithSuper(String key) { return bySuper.getOrDefault(key, List.of()); }

    @Override public List<Decl> directSubtypesOf(String key) { return byDirectSuper.getOrDefault(key, List.of()); }

    @Override public List<String> supersOf(String key) { return supersByChild.getOrDefault(key, List.of()); }

    @Override public List<Decl> declsInType(String typeKey) { return byOwner.getOrDefault(typeKey, List.of()); }

    @Override public List<String> targetKeysByName(String simpleName) {
        return List.copyOf(targetsByName.getOrDefault(simpleName, Set.of()));
    }

    @Override public Set<String> referencedKeys() { return byTarget.keySet(); }

    @Override public Stream<Decl> allDecls() { return decls.stream(); }
}
