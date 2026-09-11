package littlejlib.jacli.index;

import datapotter.arcadedbhelper.ArcadeData;
import datapotter.arcadedbhelper.TypeDef;

/** Size and mtime per source file, which is how the index knows what to re-analyse. No content hashing. */
@ArcadeData
public final class JFile extends JFile_A {

    String fileId;
    String projectId;
    String rel;
    Long size;
    Long mtime;

    public static final TypeDef<JFile> TYPEDEF = schemaBuilder()
            .factory(JFile::new)
            .unique($fileId)
            .lsmIndex($projectId)
            .__();

    public static String id(String projectId, String rel) { return projectId + "|" + rel; }
}
