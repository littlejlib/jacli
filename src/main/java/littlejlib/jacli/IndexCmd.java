package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.index.*;
import littlejlib.jacli.proj.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import datapotter.arcadedbhelper.Upsert;

@Command(name = "index", description = "Index a project now, and optionally keep watching it.",
        footer = {
            "",
            "You do not have to call this: any verb indexes what it needs and reindexes what changed.",
            "Call it to pay the cost up front, or to mark a project you are actively editing:",
            "",
            "  jacli index -p C:\\user\\code\\luvml\\luvjfx          index now",
            "  jacli index --watch                                 index and keep it fresh in the background",
            "  jacli index --reindex                               throw the index away and rebuild it",
            "  jacli index --unwatch                               stop watching"
        })
public final class IndexCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Option(names = "--watch", description = "Keep this project's index fresh in the background.")
    boolean watch;

    @Option(names = "--unwatch", description = "Stop watching this project.")
    boolean unwatch;

    @Override
    public Integer call() {
        try {
            var t0 = System.nanoTime();
            var store = common.store();
            var ms = (System.nanoTime() - t0) / 1_000_000;
            if (watch || unwatch) setWatched(store.root(), watch);
            var out = new StringBuilder();
            out.append("# indexed in ").append(ms).append("ms\n");
            out.append("# ").append(store.meta().line()).append('\n');
            out.append("# root: ").append(store.root()).append('\n');
            if (watch) out.append("# pinned: the daemon watches this project's source roots and keeps it "
                    + "fresh in the background, exempt from eviction\n");
            if (unwatch) out.append("# no longer watching\n");
            store.meta().warnings().forEach(w -> out.append("# ! ").append(w).append('\n'));
            System.out.print(out);
            return 0;
        } catch (IllegalStateException e) {
            System.err.println("jacli: " + e.getMessage());
            return littlejlib.jacli.query.Answer.NOT_INDEXED;
        } finally {
            if (common.ownsDb()) Db.shutdown();
        }
    }

    void setWatched(Path root, boolean on) {
        var db = Db.get().db();
        db.transaction(() -> Upsert.upsert(db, JProject.TYPEDEF)
                .key(JProject.$projectId, Home.token(root))
                .set(JProject.$watched, on)
                .save());
    }
}
