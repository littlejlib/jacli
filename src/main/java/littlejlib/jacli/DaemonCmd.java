package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.daemon.*;
import littlejlib.jacli.proj.Home;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "daemon", description = "Control the resident index process.",
        footer = {
            "",
            "  jacli daemon status     is one running, which build, how long",
            "  jacli daemon start      start one (verbs do this for you)",
            "  jacli daemon stop       stop it; the index on disk is kept",
            "  jacli daemon restart    stop and start, e.g. after rebuilding jacli",
            "",
            "You should rarely need any of this. A verb starts the daemon if none is running, and if the",
            "daemon is running older code than the client, the client restarts it automatically."
        })
public final class DaemonCmd implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", paramLabel = "<action>",
            description = "status (default), start, stop, restart.")
    String action = "status";

    @Option(names = { "-v", "--verbose" }, description = "Diagnostics on stderr.")
    boolean verbose;

    @Override
    public Integer call() {
        Consumer<String> log = verbose ? s -> System.err.println("jacli: " + s) : s -> { };
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "status" -> status();
            case "start" -> start(log);
            case "stop" -> stop(log);
            case "restart" -> {
                stop(log);
                yield start(log);
            }
            default -> {
                System.err.println("jacli: unknown action '" + action + "' (status, start, stop, restart)");
                yield littlejlib.jacli.query.Answer.USAGE;
            }
        };
    }

    int status() {
        var info = Info.read();
        if (info == null) {
            System.out.println("# no daemon running");
            System.out.println("# this client is build " + Build.STAMP);
            return 1;
        }
        var alive = info.processAlive();
        var ping = alive ? Client.ping() : null;
        System.out.println("# daemon: " + info.describe());
        System.out.println("# process alive: " + alive + ", responds: " + (ping != null));
        System.out.println("# this client is build " + Build.STAMP
                + (Build.STAMP.replace(' ', '_').equals(info.stamp().replace(' ', '_'))
                        ? " (same)" : " (DIFFERENT - the next verb will restart the daemon)"));
        System.out.println("# log: " + Home.file(Launcher.LOG));
        return alive && ping != null ? 0 : 1;
    }

    int start(Consumer<String> log) {
        try {
            var existing = Client.ping();
            if (existing != null) {
                System.out.println("# already running: " + Info.read().describe());
                return 0;
            }
            var info = Launcher.startAndWait(log);
            System.out.println("# started: " + info.describe());
            return 0;
        } catch (IOException e) {
            System.err.println("jacli: could not start the daemon: " + e.getMessage());
            return 1;
        }
    }

    int stop(Consumer<String> log) {
        if (Client.stop(log)) {
            System.out.println("# stopped");
            return 0;
        }
        System.out.println("# no daemon running");
        return 0;
    }
}
