package littlejlib.jacli.index;

import xyz.jphil.arcadedb.datahelper.ArcadeData;
import xyz.jphil.arcadedb.datahelper.TypeDef;

/**
 * One "child declares super" edge, flattened into a row: a type's direct supertype, or a method's
 * overridden method. Held as its own type with an index on {@code superKey} so "what implements this"
 * and "what overrides this" are single indexed lookups rather than a hierarchy walk at query time,
 * and without depending on collection-element indexing.
 */
@ArcadeData
public final class JSuper extends JSuper_A {

    String superId;
    String projectId;
    String childKey;
    String superKey;
    Boolean direct;

    public static final TypeDef<JSuper> TYPEDEF = schemaBuilder()
            .factory(JSuper::new)
            .unique($superId)
            .lsmIndex($superKey)
            .lsmIndex($childKey)
            .lsmIndex($projectId)
            .__();

    public static String id(String projectId, String childKey, String superKey) {
        return projectId + "|" + childKey + "|" + superKey;
    }
}
