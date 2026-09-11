package littlejlib.jacli.index;

import module java.base;
import datapotter.arcadedbhelper.ArcadeDoc_I;
import datapotter.arcadedbhelper.Query;

/**
 * Reads a query without letting one unreadable row destroy the answer, and says what it lost.
 * <p>
 * The mechanism is the library's — {@link Query#skippingUnreadable()} — because an index entry
 * outliving its record is an ArcadeDB concern, not a jacli one. What stays here is the sentence the
 * user reads, which has to name jacli's own repair and must not be silent: a short answer presented
 * as a complete one is worse than the crash it replaced.
 */
public final class Safe {

    private Safe() { }

    public static <E extends ArcadeDoc_I<E>> List<E> list(Query<E> query) {
        var rows = query.skippingUnreadable().list();
        if (query.unreadableSkipped() > 0) report(query.unreadableSkipped());
        return rows;
    }

    public static <E extends ArcadeDoc_I<E>> Stream<E> stream(Query<E> query) { return list(query).stream(); }

    /**
     * Names the repair that actually works. {@code --reindex} is the obvious advice and it is WRONG:
     * a forced reindex of the affected project leaves the same skip counts, because an orphaned entry
     * has no record for a delete to find.
     */
    static void report(long skipped) {
        System.err.println("jacli: " + skipped + " index " + (skipped == 1 ? "row" : "rows")
                + " could not be read - the index holds entries pointing at records that are gone, so "
                + "the answer is INCOMPLETE and reference counts in particular may be far too low. "
                + "Repair with: jacli cache --repair   (--reindex does NOT clear this)");
    }
}
