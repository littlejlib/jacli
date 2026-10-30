package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "api", description = "What a type or package offers, with how often each thing is used.",
        footer = {
            "",
            "This is the discovery verb: use it when you know roughly what you want but not what it is",
            "called. --name searches declaration names, which is a semantic search no text tool can do -",
            "it matches declarations, never comments, strings or unrelated text.",
            "",
            "  jacli api luvs.P                    every public member of P",
            "  jacli api luvs.P --name flex        just the flex ones",
            "  jacli api --name flex               anything named *flex* anywhere in the project",
            "  jacli api luvml.element             the types in that package",
            "  jacli api luvml.E --doc             with javadoc first sentences",
            "",
            "Use counts are shown when the result is small enough to be worth counting; narrow the scope",
            "or add --name to get them. For a whole unfamiliar library, jacli tree first is cheaper."
        })
public final class ApiCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", arity = "0..*", paramLabel = "<type-or-package>",
            description = { "A type, or a package prefix. Omit to search the whole project with --name.",
                "Several may be given: they are answered together against one index load." })
    List<String> scopes = new ArrayList<>();

    @Option(names = "--name", paramLabel = "<pattern>",
            description = { "Keep only declarations whose name matches, case-insensitively. One of:",
                "  post              a substring",
                "  post|reply|send   any of these substrings - one call for a family of names",
                "  get*Name          a glob, * any run and ? one character, anchored both ends",
                "  ~^on[A-Z]         a full regular expression, after the ~" })
    String name;

    @Option(names = "--doc", description = "Include the javadoc first sentence of each declaration.")
    boolean doc;

    @Option(names = "--inherited",
            description = "Everything callable on the type, its own members plus every supertype's, labelled by where each came from.")
    boolean inherited;

    @Override
    public Integer call() {
        try {
            if (scopes.isEmpty() && name == null) {
                System.err.println("jacli: give a type or package, or --name <substring>, or both. "
                        + "jacli tree lists what exists.");
                return Answer.USAGE;
            }
            Fmt.beginAnswer();
            var store = common.store();
            var opts = common.opts();
            /* No scope at all is still one question - "anything named X anywhere" - so the null
             * stands in for it rather than being skipped. */
            var asked = scopes.isEmpty() ? Collections.<String>singletonList(null) : scopes;
            var text = new StringBuilder();
            var exits = new ArrayList<Integer>();
            for (var scope : asked) {
                var answer = inherited
                        ? Discover.inherited(store, scope, name, doc, opts)
                        : Discover.api(store, scope, name, doc, opts);
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
