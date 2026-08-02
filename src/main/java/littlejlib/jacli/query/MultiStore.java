package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/**
 * Several projects answered as one. Built for the shape this toolchain actually has: a family of
 * sibling maven projects with no root pom, where the type system lives in one and the implementations
 * in another, so the most valuable structural questions fall exactly on the seam.
 * <p>
 * Never the default. A wider scope costs a query per member store, and single-project answers are the
 * common case — so this is opt in, via a declared group or an ad-hoc {@code --with}.
 * <p>
 * Merging is safe because paths are absolutised as rows leave each store: a hit from a sibling project
 * carries its own absolute path and cannot be resolved against the wrong root.
 */
public final class MultiStore implements Store {

    final List<Store> stores;
    final Store primary;
    final Meta meta;

    public MultiStore(List<Store> stores, String scopeName) {
        if (stores.isEmpty()) throw new IllegalArgumentException("a scope needs at least one project");
        this.stores = List.copyOf(stores);
        this.primary = stores.getFirst();
        this.meta = merge(stores, scopeName);
    }

    static Meta merge(List<Store> stores, String scopeName) {
        var first = stores.getFirst().meta();
        var tier = stores.stream().map(s -> s.meta().tier()).max(Comparator.comparingInt(Enum::ordinal))
                .orElse(first.tier());
        var notes = new LinkedHashSet<String>();
        stores.forEach(s -> notes.addAll(s.meta().notes()));
        var stale = stores.stream().filter(s -> !s.meta().fresh()).map(s -> s.meta().project()).toList();
        return new Meta("scope " + scopeName + ": "
                + stores.stream().map(s -> s.meta().project()).collect(Collectors.joining(", ")),
                stores.getFirst().root(), tier,
                stores.stream().mapToInt(s -> s.meta().files()).sum(),
                stores.stream().mapToInt(s -> s.meta().declCount()).sum(),
                stores.stream().mapToInt(s -> s.meta().refCount()).sum(),
                stores.stream().mapToInt(s -> s.meta().javacErrors()).sum(),
                stores.stream().mapToInt(s -> s.meta().unresolved()).sum(),
                stores.stream().map(s -> s.meta().classpathSource()).distinct().collect(Collectors.joining("+")),
                stale.isEmpty(), stale.isEmpty() ? "" : "stale in " + stale, List.copyOf(notes));
    }

    @Override public Path root() { return primary.root(); }

    @Override public Meta meta() { return meta; }

    @Override
    public List<Decl> declsByName(String simpleName) { return flat(s -> s.declsByName(simpleName)); }

    @Override
    public Optional<Decl> declByKey(String key) {
        for (var s : stores) {
            var hit = s.declByKey(key);
            if (hit.isPresent()) return hit;
        }
        return Optional.empty();
    }

    @Override public List<Occurrence> refsTo(String targetKey) { return flat(s -> s.refsTo(targetKey)); }

    @Override public List<Occurrence> refsFrom(String enclosingKey) { return flat(s -> s.refsFrom(enclosingKey)); }

    @Override public List<Decl> declsWithSuper(String key) { return dedupe(flat(s -> s.declsWithSuper(key))); }

    @Override public List<Decl> directSubtypesOf(String key) { return dedupe(flat(s -> s.directSubtypesOf(key))); }

    @Override
    public List<String> supersOf(String key) {
        return stores.stream().flatMap(s -> s.supersOf(key).stream()).distinct().toList();
    }

    @Override public List<Decl> declsInType(String typeKey) { return dedupe(flat(s -> s.declsInType(typeKey))); }

    @Override
    public List<String> targetKeysByName(String simpleName) {
        return stores.stream().flatMap(s -> s.targetKeysByName(simpleName).stream()).distinct().toList();
    }

    @Override
    public Set<String> referencedKeys() {
        var out = new HashSet<String>();
        stores.forEach(s -> out.addAll(s.referencedKeys()));
        return out;
    }

    @Override public Stream<Decl> allDecls() { return stores.stream().flatMap(Store::allDecls); }

    <T> List<T> flat(Function<Store, List<T>> f) {
        return stores.stream().flatMap(s -> f.apply(s).stream()).toList();
    }

    /** The same type can be reached from two member projects; report it once. */
    static List<Decl> dedupe(List<Decl> ds) {
        var seen = new LinkedHashMap<String, Decl>();
        ds.forEach(d -> seen.putIfAbsent(d.key() + "|" + d.file() + "|" + d.line(), d));
        return List.copyOf(seen.values());
    }
}
