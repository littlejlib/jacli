package littlejlib.jacli.claude;

import module java.base;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "install", description = "Write the prompt, one CLAUDE.md import line and the hooks. Idempotent.")
public final class ClaudeInstall implements Callable<Integer> {

    @Mixin
    Where where;

    @Option(names = "--dry-run", description = "Show what would be written, write nothing.")
    boolean dry;

    @Override
    public Integer call() throws IOException {
        var p = where.place();
        var log = new Installer(p, dry).install();
        System.out.println("# jacli claude install - " + p.scope() + " scope, " + p.root());
        log.forEach(l -> System.out.println("  " + l));
        System.out.println(log.isEmpty() ? "already installed and current - nothing changed"
                : dry ? "dry run - nothing written" : "installed. New Claude Code sessions pick it up; a running one does not.");
        return 0;
    }
}
