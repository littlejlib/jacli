package littlejlib.jacli.index;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

/**
 * One reference occurrence. This is the bulk of the index — 13067 rows for a 488-file project — and
 * the reason indexing everything is affordable: extracting all of them costs 66 ms once, against
 * the 11-24 seconds javac spends attributing the trees they came from.
 * <p>
 * {@code targetName} exists so a reference to something declared in a dependency is still findable
 * by name, and {@code file} is indexed so one changed file can be re-extracted on its own.
 */
@ArcadeData
public final class JRef extends JRef_A {

    String projectId;
    String targetKey;
    String targetName;
    String enclosingKey;
    String file;
    Integer line;
    Integer col;
    String kind;

    public static final TypeDef<JRef> TYPEDEF = schemaBuilder()
            .factory(JRef::new)
            .lsmIndex($targetKey)
            .lsmIndex($targetName)
            .lsmIndex($enclosingKey)
            .lsmIndex($file)
            .lsmIndex($projectId)
            .__();
}
