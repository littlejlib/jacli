package littlejlib.jacli.claude;

import module java.base;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "claude", synopsisSubcommandLabel = "<print|install|uninstall|status>",
        description = "Put jacli in front of Claude Code: its agent prompt and hooks, embedded in this jar.",
        subcommands = { ClaudePrint.class, ClaudeInstall.class, ClaudeUninstall.class, ClaudeStatus.class, ClaudeHook.class },
        footer = {
            "",
            "  jacli claude print                        the prompt and the hooks, to read or paste by hand",
            "  jacli claude install                      into ~/.claude (or CLAUDE_CONFIG_DIR): every session",
            "  jacli claude install --scope project      into this project's CLAUDE.md and .claude/settings.json",
            "  jacli claude status                       what is installed, and whether it is current",
            "  jacli claude uninstall                    remove exactly what install added",
            "",
            "Install writes the prompt as its own file (jacli.md) with ONE import line in CLAUDE.md, and merges",
            "three hooks into settings.json without touching any other key or hook. Running it twice changes",
            "nothing. --dry-run on install and uninstall shows the files it would write. The hooks call",
            "'jacli claude hook <event>', so jacli must stay on PATH; they need bash and nothing else."
        })
public final class ClaudeCmd implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
