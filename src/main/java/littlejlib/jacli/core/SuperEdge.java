package littlejlib.jacli.core;

/**
 * A "child is a subtype of super" link. The transitive closure is recorded, not only direct parents,
 * because a hierarchy routinely leaves the project: luvml's classes reach {@code luvx.Frag_I} only
 * through types declared in luvx-base. javac already knows the whole chain from the classpath jar, so
 * recording only direct links threw away the answer to the most valuable structural question about a
 * library.
 *
 * @param direct whether this is an immediate supertype, kept so a hierarchy can still be printed with
 *     its real shape rather than flattened under the queried type.
 */
public record SuperEdge(String childKey, String superKey, boolean direct) {

    public String id() { return childKey + "|" + superKey; }
}
