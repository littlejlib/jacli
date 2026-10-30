package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/**
 * A symbol spec resolved against a store. Keys without declarations are kept, because a reference to
 * something declared in a dependency is still a reference worth reporting.
 * <p>
 * {@code specKeys} is what the spec itself matched; {@code keys} is that plus any override chain the
 * caller asked to include. Ambiguity is a property of the SPEC and must be judged on {@code specKeys}
 * alone — judging it on {@code keys} made {@code --overrides} refuse every symbol that actually had
 * overrides, which is every symbol the flag exists for, and then offer the failing input as the fix.
 */
public record SymbolTarget(List<Decl> decls, List<String> specKeys, List<String> keys,
                           List<String> unresolvedKeys) {

    public boolean isEmpty() { return keys.isEmpty() && unresolvedKeys.isEmpty(); }

    public boolean isAmbiguous() { return specKeys.size() > 1; }

    public int expandedBy() { return keys.size() - specKeys.size(); }

    public Decl primary() { return decls.isEmpty() ? null : decls.getFirst(); }

    public static SymbolTarget resolve(Store store, Spec spec, Opts opts) {
        var specKeys = new LinkedHashSet<String>();
        var decls = store.declsMatching(spec, opts);
        decls.stream().map(Decl::key).filter(Objects::nonNull).forEach(specKeys::add);
        /* Referenced keys are consulted ALWAYS, not only when no declaration matched. Stopping at the
         * first declaration hid a real call site: a record accessor's reference row existed, but a
         * same-named field and private method matched first, so it was never looked for. */
        store.targetKeysByName(spec.searchName()).stream()
                .filter(k -> !Keys.isUnresolved(k) && spec.matchesKey(k) && opts.acceptsKey(k))
                .forEach(specKeys::add);

        var keys = new LinkedHashSet<>(specKeys);
        if (opts.overrides())
            for (var k : specKeys)
                store.declsWithSuper(k).stream().map(Decl::key).filter(Objects::nonNull).forEach(keys::add);

        var unresolved = new LinkedHashSet<String>();
        var spelling = Keys.unresolved(spec.searchName());
        if (!store.refsTo(spelling).isEmpty()) unresolved.add(spelling);
        return new SymbolTarget(decls, List.copyOf(specKeys), List.copyOf(keys), List.copyOf(unresolved));
    }

    public List<Occurrence> occurrences(Store store, Opts opts) {
        var out = new ArrayList<Occurrence>();
        for (var k : keys) store.refsTo(k).stream().filter(o -> opts.accepts(o.kind())).forEach(out::add);
        return out;
    }

    /** Every occurrence regardless of a {@code --kind} filter, so an empty filtered result can say so. */
    public int totalOccurrences(Store store) {
        var n = 0;
        for (var k : keys) n += store.refCount(k);
        return n;
    }

    public List<Occurrence> unresolvedOccurrences(Store store, Opts opts) {
        var out = new ArrayList<Occurrence>();
        for (var k : unresolvedKeys) store.refsTo(k).stream().filter(o -> opts.accepts(o.kind())).forEach(out::add);
        return out;
    }
}
