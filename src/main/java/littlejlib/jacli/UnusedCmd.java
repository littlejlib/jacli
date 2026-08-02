package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "unused", description = "Public API that nothing inside this project references.",
        footer = {
            "",
            "Candidates, not conclusions: a library's public API is called by its consumers, and they are",
            "outside the index. Overriders, constructors and main are excluded because they are reached",
            "from outside by construction."
        })
public final class UnusedCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Override
    public Integer call() {
        try {
            var answer = Graph.unused(common.store(), common.opts());
            System.out.print(answer.text());
            return answer.exit();
        } catch (IllegalStateException e) {
            System.err.println("jacli: " + e.getMessage());
            return Answer.NOT_INDEXED;
        }
    }
}
