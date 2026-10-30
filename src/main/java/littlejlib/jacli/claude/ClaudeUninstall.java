package littlejlib.jacli.claude;

import module java.base;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "uninstall", description = "Remove exactly what install added; every other line, key and hook stays.")
public final class ClaudeUninstall implements Callable<Integer> {

    @Mixin
    Where where;

    @Option(names = "--dry-run", description = "Show what would be removed, change nothing.")
    boolean dry;

    @Override
    public Integer call() throws IOException {
        var p = where.place();
        var log = new Installer(p, dry).uninstall();
        System.out.println("# jacli claude uninstall - " + p.scope() + " scope, " + p.root());
        log.forEach(l -> System.out.println("  " + l));
        System.out.println(log.isEmpty() ? "nothing of jacli's was installed there" : dry ? "dry run - nothing changed" : "uninstalled");
        return 0;
    }
}
