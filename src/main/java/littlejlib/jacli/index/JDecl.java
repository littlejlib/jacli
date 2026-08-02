package littlejlib.jacli.index;

import xyz.jphil.arcadedb.datahelper.ArcadeData;
import xyz.jphil.arcadedb.datahelper.TypeDef;

/**
 * A declaration. {@code endLine} is what makes a source view a file slice rather than a compilation,
 * and {@code name} is indexed because a symbol spec always arrives as a name.
 * <p>
 * Parameter types are stored joined rather than as a list: the key already carries them, and a flat
 * string needs no collection indexing.
 */
@ArcadeData
public final class JDecl extends JDecl_A {

    String declId;
    String projectId;
    String key;
    String name;
    String kind;
    String ownerKey;
    String file;
    Integer line;
    Integer col;
    Integer endLine;
    String signature;
    String modifiers;
    String returnType;
    String params;
    String doc;
    Boolean implicit;

    public static final TypeDef<JDecl> TYPEDEF = schemaBuilder()
            .factory(JDecl::new)
            .unique($declId)
            .lsmIndex($name)
            .lsmIndex($key)
            .lsmIndex($ownerKey)
            .lsmIndex($file)
            .lsmIndex($projectId)
            .__();

    public static String id(String projectId, String key, String file, int line) {
        return projectId + "|" + key + "|" + file + "|" + line;
    }
}
