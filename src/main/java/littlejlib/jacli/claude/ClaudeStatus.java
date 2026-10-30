package littlejlib.jacli.claude;

import module java.base;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "status", description = "What is installed and whether it matches this jacli. Exit 0 current, 1 stale or partial, 3 absent.")
public final class ClaudeStatus implements Callable<Integer> {

    @Mixin
    Where where;

    @Override
    public Integer call() throws IOException {
        var p = where.place();
        var s = new Installer(p, true).status();
        System.out.println("# jacli in Claude Code - " + p.scope() + " scope, " + p.root());
        s.lines().forEach(System.out::println);
        System.out.println(s.current() ? "installed and current" : s.absent() ? "not installed - jacli claude install"
                : "partial or stale - jacli claude install brings it up to date");
        return s.current() ? 0 : s.absent() ? 3 : 1;
    }
}
