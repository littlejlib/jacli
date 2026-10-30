package littlejlib.jacli.claude;

import module java.base;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * Called by Claude Code, not by people. A hook that fails must never block the session, so any error is
 * swallowed into exit 0 with nothing printed: the cost of a broken hook is a missing hint, not a stuck tool.
 */
@Command(name = "hook", hidden = true, description = "Run by the installed hooks: reads the hook JSON on stdin.")
public final class ClaudeHook implements Callable<Integer> {

    @Parameters(paramLabel = "<pre-bash|pre-grep|user-prompt>")
    String event;

    @Override
    public Integer call() {
        try {
            var stdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            Hooks.answer(event, stdin).ifPresent(System.out::println);
        } catch (Exception e) {
            if (System.getenv("JACLI_HOOK_DEBUG") != null) e.printStackTrace();
        }
        return 0;
    }
}
