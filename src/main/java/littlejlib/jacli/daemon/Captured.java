package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.JacliCli;
import picocli.CommandLine;

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

    public static Response run(String cwd, List<String> args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var realOut = System.out;
        var realErr = System.err;
        var realCwd = littlejlib.jacli.proj.Cwd.get();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            littlejlib.jacli.proj.Cwd.set(Path.of(cwd));
            var exit = new CommandLine(new JacliCli())
                    .setCaseInsensitiveEnumValuesAllowed(true)
                    .execute(args.toArray(String[]::new));
            return new Response(exit, Build.STAMP, out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return new Response(70, Build.STAMP, out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8) + "jacli daemon: " + e + "\n");
        } finally {
            littlejlib.jacli.proj.Cwd.set(realCwd);
            System.setOut(realOut);
            System.setErr(realErr);
        }
    }
}
