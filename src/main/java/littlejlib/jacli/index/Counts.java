package littlejlib.jacli.index;

import module java.base;
import com.arcadedb.database.Database;
import datapotter.arcadedbhelper.Upsert;
import static datapotter.arcadedbhelper.Query.query;

/** Keeps the project row's totals honest after an incremental write, so reported coverage is real. */
public final class Counts {

    private Counts() { }

    static void recount(Database db, String pid, int fileCount) {
        Upsert.upsert(db, JProject.TYPEDEF)
                .key(JProject.$projectId, pid)
                .set(JProject.$indexedAt, System.currentTimeMillis())
                .set(JProject.$files, fileCount)
                .set(JProject.$declCount, (int) query(db, JDecl.TYPEDEF).eq(JDecl.$projectId, pid).count())
                .set(JProject.$refCount, (int) query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).count())
                .save();
    }

    public static JProject project(Database db, String pid) {
        return query(db, JProject.TYPEDEF).eq(JProject.$projectId, pid).firstOrNull();
    }

    public static List<JProject> all(Database db) {
        return query(db, JProject.TYPEDEF).orderByDesc(JProject.$indexedAt).list();
    }

    public static List<JProject> inGroup(Database db, String group) {
        return query(db, JProject.TYPEDEF).eq(JProject.$scopeGroup, group).list();
    }

    /** The group a project belongs to, or null. Used to tell a caller that a wider scope exists. */
    public static String groupOf(Database db, String pid) {
        var row = project(db, pid);
        return row == null || row.scopeGroup() == null || row.scopeGroup().isBlank() ? null : row.scopeGroup();
    }

    /**
     * Removes every trace of one project. The index is a cache, but a wrong entry in it is worse than a
     * missing one — a directory indexed as an accidental catch-all keeps answering questions about files
     * that were never meant to be one project, and until now the only cure was clearing all of them.
     */
    public static boolean forget(Database db, String pid) {
        if (project(db, pid) == null) return false;
        Indexer.deleteProjectRows(db, pid);
        datapotter.arcadedbhelper.Delete.matching(
                query(db, JProject.TYPEDEF).eq(JProject.$projectId, pid));
        return true;
    }

    public static List<String> groups(Database db) {
        return all(db).stream().map(JProject::scopeGroup).filter(Objects::nonNull)
                .filter(s -> !s.isBlank()).distinct().sorted().toList();
    }
}
