package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.daemon.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "jacli", mixinStandardHelpOptions = true, version = "jacli 0.1",
        synopsisSubcommandLabel = "<verb>",
        description = "jacli (jack-lee) - java assist cli. Compiler-accurate code navigation over java/maven projects.",
        subcommands = { TreeCmd.class, ApiCmd.class, RefsCmd.class, DefCmd.class, SrcCmd.class,
                CallersCmd.class, CalleesCmd.class, ImplsCmd.class, UnusedCmd.class, DepsCmd.class,
                ReleasableCmd.class, ScopeCmd.class, IndexCmd.class, ProjectsCmd.class, CacheCmd.class, StatusCmd.class,
                DaemonCmd.class })
public final class JacliCli implements Callable<Integer> {

    @Option(names = "--brief", description = "Print the short help instead of the tutorial.")
    boolean brief;

    public static void main(String[] args) {
        utf8Out();
        var argv = List.of(args);
        if (runsLocally(argv)) System.exit(runHere(args));
        var log = logger(argv);
        try {
            var response = Client.send(argv, true, log);
            System.out.print(response.stdout());
            System.err.print(response.stderr());
            System.exit(response.exit());
        } catch (IOException e) {
            System.err.println("jacli: daemon unavailable (" + e.getMessage()
                    + "); answering in this process, which is slower");
            System.exit(runHere(args));
        }
    }

    static int runHere(String[] args) {
        return new CommandLine(new JacliCli()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
    }

    /**
     * What must not be forwarded: help and version (no index needed), daemon control (it would ask
     * the daemon to manage itself), and the explicit escape hatches, whose whole point is to bypass
     * the resident process.
     */
    static boolean runsLocally(List<String> args) {
        if (args.isEmpty() || args.getFirst().startsWith("-")) return true;
        if ("daemon".equals(args.getFirst())) return true;
        return args.stream().anyMatch(a -> a.equals("--direct") || a.equals("--syntactic")
                || a.equals("--help") || a.equals("-h"));
    }

    static Consumer<String> logger(List<String> args) {
        var verbose = args.contains("-v") || args.contains("--verbose");
        return verbose ? s -> System.err.println("jacli: " + s) : s -> { };
    }

    /**
     * jacli's own text is ASCII, but it echoes source lines, and a source line can hold anything a
     * java string literal can. On Windows System.out defaults to the console codepage, which would
     * mangle those, so write UTF-8 explicitly and let the terminal's chcp decide only how it looks.
     */
    static void utf8Out() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }

    @Override
    public Integer call() {
        System.out.print(brief ? Tutorial.brief() : Tutorial.full());
        return 0;
    }
}
