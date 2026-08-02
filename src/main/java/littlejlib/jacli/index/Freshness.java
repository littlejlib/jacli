package littlejlib.jacli.index;

import module java.base;
import littlejlib.jacli.proj.JavaProject;

/**
 * Whether something already knows a project is unchanged, so a query can skip its fingerprint sweep.
 * <p>
 * Implemented by the daemon's watcher, which holds a live filesystem watch and has swept since the last
 * event. Absent that, every query sweeps — which is correct and was the only behaviour until now. The
 * shortcut exists to move that cost off the query path, and must never be the reason an answer is wrong,
 * so a watcher that has lost events is required to stop vouching.
 */
public interface Freshness {

    Freshness ALWAYS_SWEEP = new Freshness() {

        @Override
        public JavaProject verifiedProject(String pid) { return null; }

        @Override
        public void vouch(String pid, JavaProject project) { }
    };

    /** The project as last resolved, if it is known to be unchanged since. Null means sweep. */
    JavaProject verifiedProject(String pid);

    /** Records that a sweep just found this project clean, with the resolution it produced. */
    void vouch(String pid, JavaProject project);
}
