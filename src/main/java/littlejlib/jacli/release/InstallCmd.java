package littlejlib.jacli.release;

import module java.base;
import jarrunner.jr.apputils.Install;
import jarrunner.jr.apputils.JrApp;
import littlejlib.jacli.daemon.Client;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "install", description = "Install this jacli.exe, with jacli-daemon.exe, for this user.",
        footer = {
            "",
            "  curl.exe -fLo jacli.exe https://github.com/littlejlib/jacli/releases/latest/download/jacli.exe",
            "  .\\jacli.exe install",
            "",
            "Copies the exes to %LOCALAPPDATA%\\Programs\\jacli and puts that folder first on your user PATH",
            "(a new terminal sees it). No administrator rights. Running it again repairs the install.",
            "Then: jacli claude install"
        })
public final class InstallCmd implements Callable<Integer> {

    @Option(names = "--no-path", description = "Do not touch the user PATH.")
    boolean noPath;

    @Option(names = "--dir", paramLabel = "<folder>", description = "Install into this folder instead of %LOCALAPPDATA%\\Programs\\jacli.")
    Path dir;

    @Override
    public Integer call() {
        var self = JrApp.exe();
        if (self.isEmpty()) {
            System.err.println("jacli: install runs from jacli.exe, not from a jar. Download it: "
                    + "https://github.com/littlejlib/jacli/releases/latest/download/jacli.exe");
            return 1;
        }
        Client.stop(s -> { });
        var install = Install.app("jacli").displayName("jacli").description("jacli - java assist cli")
                .menuShortcut(false).onPath(!noPath);
        var result = (dir == null ? install : install.installDir(dir.toAbsolutePath())).run();
        if (result.command() == null) return 1;
        var problems = new ArrayList<>(result.problems());
        problems.addAll(Siblings.place(self.get().getParent(), result.command().getParent(), System.out::println));
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("jacli: " + p));
            return 1;
        }
        System.out.println("jacli " + Version.number() + " is installed in " + result.command().getParent()
                + (noPath ? "" : ". Open a new terminal, then run: jacli claude install"));
        return 0;
    }
}
