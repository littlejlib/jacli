package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.daemon.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "jacli", mixinStandardHelpOptions = true, version = "jacli 0.1",
        synopsisSubcommandLabel = "<verb>",
        description = "jacli (jack-lee) - java assist cli. Compiler-accurate code navigation over java/maven projects.",
        subcommands = { TreeCmd.class, ApiCmd.class, RefsCmd.class, DefCmd.class, SrcCmd.class, AtCmd.class,
                CallersCmd.class, CalleesCmd.class, ImplsCmd.class, UnusedCmd.class, DepsCmd.class,
                ReleasableCmd.class, DiscoverCmd.class, ScopeCmd.class, IndexCmd.class, ProjectsCmd.class,
                CacheCmd.class, StatusCmd.class, DaemonCmd.class, TutorialCmd.class, littlejlib.jacli.claude.ClaudeCmd.class })
public final class JacliCli implements Callable<Integer> {

    @Option(names = "--brief", description = "Print the short help. This is now the default; kept so older invocations still work.")
    boolean brief;

    @Option(names = "--full", description = "Print the full tutorial, the same text as jacli tutorial.")
    boolean full;

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

    /**
     * In-process runs need the same progress the daemon sends over the wire — {@code --direct} and the
     * fallback when no daemon can be reached are exactly the slow paths, so they are the last place
     * that should be silent. Here the sink is simply stderr, with no framing in between.
     */
    static int runHere(String[] args) {
        littlejlib.jacli.query.Fmt.beginAnswer();
        littlejlib.jacli.core.Progress.sink(note -> {
            System.err.println("jacli: " + note);
            System.err.flush();
        });
        try {
            return commandLine().execute(args);
        } finally {
            littlejlib.jacli.core.Progress.sink(null);
        }
    }

    /**
     * One place both entry points build the parser, so a run inside the daemon cannot be configured
     * differently from a run in this process. {@link littlejlib.jacli.daemon.Captured} was already
     * assembling its own, which is how the subcommands ended up without the help option below.
     */
    public static CommandLine commandLine() {
        var cli = new CommandLine(new JacliCli()).setCaseInsensitiveEnumValuesAllowed(true);
        cli.getSubcommands().values().forEach(JacliCli::addHelpOption);
        return cli;
    }

    /**
     * The tutorial tells the reader to run {@code jacli <verb> --help}, and every verb rejected it with
     * exit 2: the standard help mixin is declared on this command only, so picocli never gave the
     * subcommands a {@code -h}. Only the usage-help option is added, not the whole mixin, because the
     * mixin's {@code -V} would print nothing on a subcommand that carries no version of its own.
     */
    static void addHelpOption(CommandLine sub) {
        var spec = sub.getCommandSpec();
        if (spec.findOption("--help") != null) return;
        spec.addOption(CommandLine.Model.OptionSpec.builder("-h", "--help").usageHelp(true)
                .description("Show this help message and exit.").build());
    }

    /**
     * What must not be forwarded: help and version (no index needed), daemon control (it would ask
     * the daemon to manage itself), and the explicit escape hatches, whose whole point is to bypass
     * the resident process.
     */
    static boolean runsLocally(List<String> args) {
        if (args.isEmpty() || args.getFirst().startsWith("-")) return true;
        if ("daemon".equals(args.getFirst()) || "claude".equals(args.getFirst())) return true;
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
        System.out.print(full && !brief ? Tutorial.full() : Tutorial.brief());
        return 0;
    }
}
