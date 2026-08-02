package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "tree", description = "Packages and type names. The cheapest way to see what a project contains.",
        footer = {
            "",
            "Start here when you do not yet know a name. Costs almost nothing, then use api or def on",
            "whatever looks right.",
            "",
            "  jacli tree                    every package and the types in it",
            "  jacli tree luvml.element      just that branch",
            "  jacli tree -p ..\\luvs        another project"
        })
public final class TreeCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<package-prefix>",
            description = "Restrict to a package prefix. Omit for the whole project.")
    String prefix;

    @Override
    public Integer call() {
        try {
            var answer = Discover.tree(common.store(), prefix);
            common.emit(answer.text());
            return answer.exit();
        } catch (IllegalStateException e) {
            System.err.println("jacli: " + e.getMessage());
            return Answer.NOT_INDEXED;
        } finally {
            if (common.ownsDb()) littlejlib.jacli.index.Db.shutdown();
        }
    }
}
