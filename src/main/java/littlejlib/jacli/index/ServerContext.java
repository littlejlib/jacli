package littlejlib.jacli.index;

/**
 * Lets the daemon lend its already-open index to the command it is executing. The daemon runs the
 * very same picocli verbs the CLI does, so there is one implementation of each verb rather than a
 * local one and a remote one that can drift apart.
 */
public final class ServerContext {

    private ServerContext() { }

    private static volatile ProjectIndex shared;

    public static void install(ProjectIndex index) { shared = index; }

    /** The daemon's index, or null when running as a plain process. */
    public static ProjectIndex index() { return shared; }
}
