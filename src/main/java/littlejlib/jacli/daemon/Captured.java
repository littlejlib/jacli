package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.JacliCli;

/**
 * Runs a real CLI invocation inside the daemon with its streams captured. The daemon therefore
 * executes the same verbs, with the same options and the same exit codes, as a direct run — the one
 * way to be sure the remote and local paths cannot answer differently.
 * <p>
 * Swapping System.out is process-global, which is safe only because {@link Server} holds a lock for
 * the duration of a request.
 */
public final class Captured {

    private Captured() { }

    /**
     * Whether the request being served asked for diagnostics. Process-global for the same reason the
     * streams above are: {@link Server} holds a lock for the whole request. It exists so the daemon's
     * own housekeeping chatter — cache evictions, which are routine and never change an answer — stays
     * out of an ordinary reply and remains available under {@code -v}.
     */
    public static volatile boolean verbose;

    public static Response run(String cwd, List<String> args) {
        return run(cwd, args, note -> { });
    }

    public static Response run(String cwd, List<String> args, Consumer<String> onNote) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var realOut = System.out;
        var realErr = System.err;
        var realCwd = littlejlib.jacli.proj.Cwd.get();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            littlejlib.jacli.proj.Cwd.set(Path.of(cwd));
            verbose = args.contains("-v") || args.contains("--verbose");
            /* Worker threads are pooled and the banner guard is a ThreadLocal, so a request that
             * inherited a set flag from the previous one on this thread would print no tier line and
             * no warnings at all - the single failure mode this tool must never have. */
            littlejlib.jacli.query.Fmt.beginAnswer();
            littlejlib.jacli.core.Progress.sink(onNote);
            littlejlib.jacli.proj.Builds.prefer(null);
            var exit = JacliCli.commandLine().execute(args.toArray(String[]::new));
            return new Response(exit, Build.STAMP, out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return new Response(70, Build.STAMP, out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8) + "jacli daemon: " + e + "\n");
        } finally {
            littlejlib.jacli.proj.Cwd.set(realCwd);
            System.setOut(realOut);
            System.setErr(realErr);
            verbose = false;
            littlejlib.jacli.core.Progress.sink(null);
            littlejlib.jacli.proj.Builds.prefer(null);
        }
    }
}
