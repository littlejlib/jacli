package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/**
 * The primitive lookups every verb is built from. Two implementations: an in-memory one for
 * {@code --direct} and for tests, and the pack-backed one the daemon uses. Verbs are written once,
 * against this, so a query cannot behave differently depending on which is in play.
 */
public interface Store {

    Path root();

    Meta meta();

    /** Declarations whose simple name matches — the indexed entry point for every symbol spec. */
    List<Decl> declsByName(String simpleName);

    Optional<Decl> declByKey(String key);

    List<Occurrence> refsTo(String targetKey);

    default int refCount(String targetKey) { return refsTo(targetKey).size(); }

    List<Occurrence> refsFrom(String enclosingKey);

    /**
     * Declarations that have {@code key} anywhere above them — the transitive answer, so a subtype
     * reached through a type declared in another project is still found.
     */
    List<Decl> declsWithSuper(String key);

    /** Declarations whose immediate supertype is {@code key}, used to print a hierarchy's real shape. */
    List<Decl> directSubtypesOf(String key);

    /**
     * The supertype keys recorded for {@code key}, looked up directly.
     * <p>
     * Exists because {@code declByKey} deliberately does not attach supertypes, and reading
     * {@code superKeys()} off it therefore returns an empty list that looks like an answer. That cost two
     * separate bugs — a type with six supertypes reported as having none, and a hierarchy walk that
     * stopped at the first type — so the trap is removed rather than avoided.
     */
    List<String> supersOf(String key);

    List<Decl> declsInType(String typeKey);

    /**
     * Distinct reference targets whose simple name matches. Lets a query reach symbols that have no
     * declaration in this project — anything from a dependency jar or the JDK.
     */
    List<String> targetKeysByName(String simpleName);

    /**
     * Every key that is referenced at least once. One query, so {@code unused} can test membership
     * instead of asking for each declaration's references in turn.
     */
    Set<String> referencedKeys();

    Stream<Decl> allDecls();

    default List<Decl> declsMatching(Spec spec) { return declsMatching(spec, Opts.defaults()); }

    default List<Decl> declsMatching(Spec spec, Opts opts) {
        var byName = declsByName(spec.searchName());
        var hits = byName.stream().filter(spec::matches).filter(opts::acceptsDecl).toList();
        if (!hits.isEmpty()) return hits;
        if (spec.isMember()) return List.of();
        var asMember = Spec.parse("#" + spec.typeSpec());
        return byName.stream().filter(asMember::matches).filter(opts::acceptsDecl).toList();
    }
}
