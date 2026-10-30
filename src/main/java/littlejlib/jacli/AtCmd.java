package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "at", description = "What declaration is at this file and line, and what it references there.",
        footer = {
            "",
            "The way IN to everything else, for when you have a position rather than a name - a grep hit,",
            "a stack frame, a compiler error, a line in a diff. It names the declaration containing that",
            "line and every symbol used on it, each as a spec you can paste straight into another verb.",
            "",
            "  jacli at src/main/java/x/ChatTool.java:1183     what am I looking at",
            "  jacli at ChatTool.java:1183                     the bare file name is enough",
            "  jacli at x/ChatTool.java:1183:22                a column is accepted and ignored",
            "  jacli at ChatTool.java                          no line: everything the file declares",
            "",
            "This replaces reading upwards through a file to find which method a line belongs to."
        })
public final class AtCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", arity = "1..*", paramLabel = "<file:line>",
            description = "A file, optionally with :line or :line:col. Several may be given.")
    List<String> places = new ArrayList<>();

    @Override
    public Integer call() {
        try {
            Fmt.beginAnswer();
            var store = common.store();
            var opts = common.opts();
            var text = new StringBuilder();
            var exits = new ArrayList<Integer>();
            for (var place : places) {
                var answer = At.at(store, place, opts);
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
