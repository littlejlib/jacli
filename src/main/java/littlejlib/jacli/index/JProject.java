package littlejlib.jacli.index;

import datapotter.datahelper.Data;

/**
 * One indexed project (a maven reactor root, so a multi-module build is one row and cross-module
 * queries need no extra registration). Everything needed to decide whether an answer is trustworthy
 * lives here, because that verdict has to survive a process restart along with the index itself.
 * <p>
 * Rows live in the catalog file; the declarations and references live in the project's own pack.
 */
@Data
public final class JProject extends JProject_A {

    String projectId;
    String root;
    String coords;
    String tier;
    String classpathSource;
    Long indexedAt;
    Long indexMs;
    Integer files;
    Integer declCount;
    Integer refCount;
    Integer javacErrors;
    Integer unresolved;
    Boolean watched;
    String notes;
    Integer formatVersion;
    String scopeGroup;

    /**
     * Bumped whenever extraction changes what a row MEANS, not just what the schema holds. Source
     * fingerprints cannot catch that — the sources are identical, only our reading of them improved —
     * so without this a semantic fix silently keeps serving rows produced by the old, wrong code.
     */
    public static final int FORMAT = 6;
}
