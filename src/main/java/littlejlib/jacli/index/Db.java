package littlejlib.jacli.index;

import module java.base;
import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import littlejlib.jacli.proj.Home;
import xyz.jphil.arcadedb.datahelper.InitDoc;

/**
 * The embedded index, opened once per process, through {@link DatabaseFactory} rather than
 * {@code ArcadeDBServer}: the server binds an HTTP port (2480-2489, which on this machine is already
 * taken by another ArcadeDB) and logs its startup to the console an agent has to parse. jacli already
 * has its own loopback protocol, so none of that is wanted — only the embedded engine is.
 * <p>
 * Only the daemon should hold this. ArcadeDB takes an exclusive lock on its directory and Claude Code
 * issues parallel tool calls, so a per-invocation open would collide with itself.
 * <p>
 * One database for every project, with a projectId on each row, so cross-project questions stay
 * possible and there is one lock rather than one per project.
 */
public final class Db {

    static {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        java.util.logging.Logger.getLogger("com.arcadedb.script.GraalPolyglotEngine")
                .setLevel(java.util.logging.Level.OFF);
        java.util.logging.Logger.getLogger("com.arcadedb").setLevel(java.util.logging.Level.WARNING);
    }

    public static final String DB_NAME = "jacli";

    /** Every type the index holds, in one place, so schema init and repair cannot drift apart. */
    public static final List<xyz.jphil.arcadedb.datahelper.TypeDef<? extends xyz.jphil.datahelper.DataHelper_I<?>>>
            TYPES = List.of(JProject.TYPEDEF, JFile.TYPEDEF, JDecl.TYPEDEF, JRef.TYPEDEF, JSuper.TYPEDEF);

    /** Type names, for the index-level operations that take no generics. */
    public static List<String> typeNames() {
        return TYPES.stream().map(t -> t.definition().getSimpleName()).toList();
    }

    private static Db instance;
    final DatabaseFactory factory;
    final Database db;

    private Db() {
        var dbPath = Home.dir("db").resolve(DB_NAME);
        factory = new DatabaseFactory(dbPath.toString());
        db = factory.exists() ? factory.open() : factory.create();
        InitDoc.initDocTypes(db, TYPES.toArray(xyz.jphil.arcadedb.datahelper.TypeDef[]::new));
    }

    public static synchronized Db get() {
        if (instance == null) instance = new Db();
        return instance;
    }

    public static synchronized boolean isOpen() { return instance != null; }

    public Database db() { return db; }

    public Path location() { return Home.dir("db").resolve(DB_NAME); }

    public static synchronized void shutdown() {
        if (instance == null) return;
        var it = instance;
        instance = null;
        try {
            if (it.db.isOpen()) it.db.close();
        } finally {
            it.factory.close();
        }
    }
}
