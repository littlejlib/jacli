package littlejlib.jacli.index;

import module java.base;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.exception.RecordNotFoundException;
import xyz.jphil.arcadedb.datahelper.InitDoc;
import static xyz.jphil.arcadedb.datahelper.Query.query;

/**
 * Reproduces the reindex corruption without javac, so a cycle costs seconds rather than the four
 * minutes teavm costs. Writes rows for a project, deletes them the way a full reindex does, writes
 * them again, and reports whether the index still answers.
 * <p>
 * Run: mvn exec:java -Dexec.mainClass=littlejlib.jacli.index.ChurnProbe -Dexec.classpathScope=test
 * -Dexec.args="&lt;rows&gt; &lt;rounds&gt;"
 */
public final class ChurnProbe {

    private ChurnProbe() { }

    public static final String PID = "victim", OTHER = "bystander", KEY = "target.Key0";

    public static void main(String[] args) throws IOException {
        var rows = args.length > 0 ? Integer.parseInt(args[0]) : 50_000;
        var rounds = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        var strategy = args.length > 2 ? args[2] : "record";
        var dir = Files.createTempDirectory("jacli-churn");
        var factory = new DatabaseFactory(dir.resolve("probe").toString());
        var db = factory.create();
        try {
            InitDoc.initDocTypes(db, JProject.TYPEDEF, JFile.TYPEDEF, JDecl.TYPEDEF, JRef.TYPEDEF, JSuper.TYPEDEF);
            write(db, OTHER, rows / 10);
            for (var round = 1; round <= rounds; round++) {
                var t0 = System.nanoTime();
                if (round > 1) purge(db, strategy);
                var deleteMs = (System.nanoTime() - t0) / 1_000_000;
                var t1 = System.nanoTime();
                write(db, PID, rows);
                System.out.println("  (write " + (System.nanoTime() - t1) / 1_000_000 + "ms)");
                report(db, round, rows, deleteMs);
            }
        } finally {
            db.close();
            factory.close();
            delete(dir);
        }
    }

    /**
     * "record" is the safe-but-slow per-record delete; "sql" is the fast bulk delete that leaves the
     * index lying, followed by the rebuild that makes it honest again.
     */
    static void purge(Database db, String strategy) {
        if ("record".equals(strategy)) {
            Indexer.deleteProjectRows(db, PID);
            return;
        }
        if ("window".equals(strategy)) {
            var t0 = System.nanoTime();
            xyz.jphil.arcadedb.datahelper.Indexes.duringBulkChange(db, Db.TYPES,
                    () -> Indexer.deleteProjectRows(db, PID));
            System.out.println("  (typed delete inside a bulk-change window "
                    + (System.nanoTime() - t0) / 1_000_000 + "ms)");
            return;
        }
        if ("unindexed".equals(strategy)) {
            var t0 = System.nanoTime();
            var dropped = xyz.jphil.arcadedb.datahelper.Indexes.drop(db, "JRef");
            var dropMs = (System.nanoTime() - t0) / 1_000_000;
            var t1 = System.nanoTime();
            db.transaction(() -> db.command("sql", "DELETE FROM JRef WHERE projectId = ?", PID));
            var deleteMs = (System.nanoTime() - t1) / 1_000_000;
            var t2 = System.nanoTime();
            xyz.jphil.arcadedb.datahelper.Indexes.rebuild(db, JRef.TYPEDEF);
            System.out.println("  (dropped " + dropped + " indexes " + dropMs + "ms, unindexed delete "
                    + deleteMs + "ms, rebuild " + (System.nanoTime() - t2) / 1_000_000 + "ms)");
            return;
        }
        var t0 = System.nanoTime();
        db.transaction(() -> db.command("sql", "DELETE FROM JRef WHERE projectId = ?", PID));
        var deleteMs = (System.nanoTime() - t0) / 1_000_000;
        var t1 = System.nanoTime();
        var rebuilt = xyz.jphil.arcadedb.datahelper.Indexes.rebuild(db, JRef.TYPEDEF);
        System.out.println("  (sql delete " + deleteMs + "ms, rebuild " + rebuilt + " indexes "
                + (System.nanoTime() - t1) / 1_000_000 + "ms)");
    }

    static void write(Database db, String pid, int rows) {
        var refs = new ArrayList<Runnable>();
        for (var i = 0; i < rows; i++) {
            var n = i;
            refs.add(() -> new JRef().projectId(pid).targetKey("target.Key" + (n % 10))
                    .targetName("Key" + (n % 10)).enclosingKey("owner.Type" + (n % 100))
                    .file("src/F" + (n % 500) + ".java").line(n).col(1).kind("CALL").in(db).insert());
        }
        for (var from = 0; from < refs.size(); from += Indexer.BATCH) {
            var chunk = refs.subList(from, Math.min(refs.size(), from + Indexer.BATCH));
            db.transaction(() -> chunk.forEach(Runnable::run));
        }
    }

    /** Counts by iterating so an unreadable row is visible rather than fatal, exactly as a verb sees it. */
    static void report(Database db, int round, int rows, long deleteMs) {
        var byProject = counted(query(db, JRef.TYPEDEF).eq(JRef.$projectId, PID));
        var byTarget = counted(query(db, JRef.TYPEDEF).eq(JRef.$projectId, PID).eq(JRef.$targetKey, KEY));
        var bystander = counted(query(db, JRef.TYPEDEF).eq(JRef.$projectId, OTHER));
        System.out.printf("round %d  delete=%5dms  byProject=%6d/%d%s  byTarget=%5d/%d%s  bystander=%d/%d%s%n",
                round, deleteMs, byProject[0], rows, bad(byProject), byTarget[0], rows / 10, bad(byTarget),
                bystander[0], rows / 10, bad(bystander));
    }

    static String bad(long[] c) { return c[1] == 0 ? "" : "  UNREADABLE=" + c[1]; }

    static long[] counted(Iterable<JRef> rows) {
        var found = 0L;
        var unreadable = 0L;
        var it = rows.iterator();
        while (true) {
            try {
                if (!it.hasNext()) break;
                it.next();
                found++;
            } catch (RecordNotFoundException e) {
                unreadable++;
                if (unreadable > 100_000) break;
            }
        }
        return new long[] { found, unreadable };
    }

    static void delete(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    // a temp directory left behind costs nothing
                }
            });
        } catch (IOException e) {
            // ditto
        }
    }
}
