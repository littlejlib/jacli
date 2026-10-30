package littlejlib.jacli.release;

import module java.base;
import jarrunner.jr.apputils.JrApp;
import littlejlib.jacli.daemon.Client;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static littlejlib.jacli.release.ExeUpdate.*;

@Command(name = "update", description = "Move jacli and jacli-daemon to the newest release.",
        footer = {
            "",
            "Stops the daemon first (the index on disk is kept; the next verb starts the new one).",
            "--check only says whether a newer release exists: exit 0 current, 10 newer, 1 error.",
            "Releases are checked once a day in the background; a newer one is mentioned on stderr."
        })
public final class UpdateCmd implements Callable<Integer> {

    @Option(names = "--check", description = "Only say whether a newer release exists; install nothing.")
    boolean check;

    @Override
    public Integer call() {
        var self = JrApp.exe();
        if (self.isEmpty()) {
            System.err.println("jacli: update runs from the installed jacli.exe, not from a jar.");
            return 1;
        }
        var exes = new ArrayList<>(Siblings.in(self.get().getParent()));
        exes.add(self.get());
        var newer = new ArrayList<Path>();
        for (var exe : exes) {
            var code = check(exe);
            if (code == ERROR) return fail(exe);
            if (code == NEWER) newer.add(exe);
            if (exe.equals(self.get()) || code != CURRENT) System.out.println(label(exe) + message);
        }
        if (newer.isEmpty()) return 0;
        return check ? NEWER : apply(newer, self.get());
    }

    int apply(List<Path> newer, Path self) {
        Client.stop(s -> { });
        for (var exe : newer) {
            if (update(exe) == ERROR) return fail(exe);
            System.out.println(label(exe) + message);
        }
        warm(self);
        System.out.println("jacli is current. The daemon starts again with the next verb.");
        return 0;
    }

    static String label(Path exe) {
        return exe.getFileName() + ": ";
    }

    static int fail(Path exe) {
        System.err.println("jacli: " + label(exe) + message);
        return 1;
    }

    /** Fetches the new jar now, rather than in the middle of the next command an agent runs. */
    static void warm(Path self) {
        try {
            new ProcessBuilder(self.toString(), "--version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor();
        } catch (IOException e) {
            // the next launch fetches it
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
