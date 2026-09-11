package littlejlib.jacli.index;

import module java.base;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import datapotter.arcadedbhelper.Indexes;
import datapotter.arcadedbhelper.InitDoc;
import datapotter.arcadedbhelper.TypeDef;
import static org.junit.jupiter.api.Assertions.*;
import static datapotter.arcadedbhelper.Query.query;

/**
 * Replacing a project's rows must leave the index agreeing with the records.
 * <p>
 * <b>Read what this does and does not cover before trusting it.</b> The failure it is named after —
 * every verb on a 2,991-file project dying with {@code RecordNotFoundException}, and then reporting 0
 * references for a symbol used 2,543 times — is SCALE DEPENDENT and does <em>not</em> reproduce here.
 * These same assertions were run against the old {@code DELETE FROM ... WHERE} implementation at 4,000
 * and at 50,000 rows and passed both times. It took roughly 370,000 rows across four types before an
 * index entry outlived its record.
 * <p>
 * So this is an INVARIANT GUARD, not a reproduction: it pins that a replace leaves every indexed
 * lookup exact, that a bulk-change window restores what it dropped even when the work throws, and that
 * a rebuild loses no rows. The evidence for the real bug is {@code ChurnProbe} for timings and the
 * teavm field test for scale — a reindex there went from leaving 65 unreadable entries and 0 references
 * to 0 and 2,543. A test that took four minutes and half a gigabyte would not be run.
 */
class IndexChurnTest {

    static final String PID = "victim", OTHER = "bystander";
    static final int ROWS = 4_000, TARGETS = 10;

    Database db;
    DatabaseFactory factory;

    @BeforeEach
    void open(@TempDir Path dir) {
        factory = new DatabaseFactory(dir.resolve("churn").toString());
        db = factory.create();
        InitDoc.initDocTypes(db, Db.TYPES.toArray(TypeDef[]::new));
    }

    @AfterEach
    void close() {
        if (db != null && db.isOpen()) db.close();
        if (factory != null) factory.close();
    }

    void writeRefs(String pid, int rows) {
        var work = new ArrayList<Runnable>();
        for (var i = 0; i < rows; i++) {
            var n = i;
            work.add(() -> new JRef().projectId(pid).targetKey("t.Key" + (n % TARGETS))
                    .targetName("Key" + (n % TARGETS)).enclosingKey("o.Type" + (n % 50))
                    .file("src/F" + (n % 100) + ".java").line(n).col(1).kind("CALL").in(db).insert());
        }
        for (var from = 0; from < work.size(); from += Indexer.BATCH) {
            var chunk = work.subList(from, Math.min(work.size(), from + Indexer.BATCH));
            db.transaction(() -> chunk.forEach(Runnable::run));
        }
    }

    long byProject(String pid) { return query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).list().size(); }

    long byTarget(String pid, String key) {
        return query(db, JRef.TYPEDEF).eq(JRef.$projectId, pid).eq(JRef.$targetKey, key).list().size();
    }

    long orphans() { return Indexes.orphans(db, "JRef"); }

    @Test
    @DisplayName("replacing a project's rows leaves every indexed lookup exact")
    void replaceKeepsIndexHonest() {
        writeRefs(OTHER, 500);
        writeRefs(PID, ROWS);
        assertEquals(ROWS, byProject(PID));

        for (var round = 0; round < 3; round++) {
            Indexer.deleteProjectRows(db, PID);
            assertEquals(0, byProject(PID), "round " + round + ": rows survived the delete");
            writeRefs(PID, ROWS);

            assertEquals(0, orphans(), "round " + round + ": index entries outlived their records");
            assertEquals(ROWS, byProject(PID), "round " + round + ": lookup by projectId");
            assertEquals(ROWS / TARGETS, byTarget(PID, "t.Key0"),
                    "round " + round + ": lookup by targetKey - this is the one that returned 0");
            assertEquals(500, byProject(OTHER), "round " + round + ": another project was disturbed");
        }
    }

    @Test
    @DisplayName("a bulk-change window rebuilds what it dropped, even when the work throws")
    void windowAlwaysRestoresIndexes() {
        writeRefs(PID, 200);
        var before = Indexes.names(db, "JRef");
        assertFalse(before.isEmpty(), "the fixture should have indexes to drop");

        assertThrows(IllegalStateException.class, () -> Indexes.duringBulkChange(db, Db.TYPES, () -> {
            throw new IllegalStateException("boom");
        }));
        assertEquals(before.size(), Indexes.names(db, "JRef").size(),
                "indexes must come back even when the bulk change fails");
        assertEquals(200, byProject(PID));
    }

    @Test
    @DisplayName("repair clears entries the records no longer back")
    void repairRebuildsFromRecords() {
        writeRefs(PID, 500);
        var rebuilt = Indexes.rebuild(db, Db.TYPES.toArray(TypeDef[]::new));
        assertTrue(rebuilt > 0, "something should have been rebuilt");
        assertEquals(0, orphans());
        assertEquals(500, byProject(PID), "a rebuild must not lose rows");
        assertEquals(50, byTarget(PID, "t.Key0"));
    }
}
