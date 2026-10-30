package littlejlib.jacli.claude;

import module java.base;
import picocli.CommandLine.Option;

public final class Where {

    @Option(names = "--scope", defaultValue = "user", paramLabel = "user|project",
            description = "user: every session of this Claude Code configuration (default). project: one project, committed with it.")
    String scope;

    @Option(names = "--config-dir", paramLabel = "<dir>",
            description = "The Claude Code configuration directory for --scope user. Default: CLAUDE_CONFIG_DIR, else ~/.claude.")
    Path configDir;

    @Option(names = "--project-dir", paramLabel = "<dir>", description = "The project for --scope project. Default: the current directory.")
    Path projectDir;

    Place place() {
        return switch (scope) {
            case "user" -> Place.user((configDir != null ? configDir : Place.defaultConfigDir()).toAbsolutePath().normalize());
            case "project" -> Place.project((projectDir != null ? projectDir : Path.of("")).toAbsolutePath().normalize());
            default -> throw new IllegalArgumentException("--scope is user or project, not " + scope);
        };
    }
}
