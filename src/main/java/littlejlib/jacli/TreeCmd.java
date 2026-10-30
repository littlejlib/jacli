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

    @Parameters(index = "0", arity = "0..*", paramLabel = "<package-prefix>",
            description = { "Restrict to a package prefix. Omit for the whole project.",
                "Several may be given: they are answered together against one index load." })
    List<String> prefixes = new ArrayList<>();

    @Override
    public Integer call() {
        try {
            littlejlib.jacli.query.Fmt.beginAnswer();
            var store = common.store();
            var asked = prefixes.isEmpty() ? Collections.<String>singletonList(null) : prefixes;
            var text = new StringBuilder();
            var exits = new ArrayList<Integer>();
            for (var prefix : asked) {
                var answer = Discover.tree(store, prefix);
                if (!exits.isEmpty()) text.append('\n');
                text.append(answer.text());
                exits.add(answer.exit());
            }
            common.emit(text.toString());
            return QueryCmd.combine(exits);
        } catch (IllegalStateException e) {
            System.err.println("jacli: " + e.getMessage());
            return Answer.NOT_INDEXED;
        }
    }
}
