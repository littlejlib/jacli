package littlejlib.jacli.claude;

import module java.base;

/**
 * Where each piece goes for one scope. The prompt is its own file and CLAUDE.md gains only an import line,
 * so the user's own CLAUDE.md is touched by one line that uninstall can find again exactly.
 */
record Place(String scope, Path root, Path claudeMd, Path prompt, String importLine, Path settings) {

    static Place user(Path configDir) {
        return new Place("user", configDir, configDir.resolve("CLAUDE.md"), configDir.resolve("jacli.md"),
                "@jacli.md", configDir.resolve("settings.json"));
    }

    static Place project(Path dir) {
        return new Place("project", dir, dir.resolve("CLAUDE.md"), dir.resolve(".claude/jacli.md"),
                "@.claude/jacli.md", dir.resolve(".claude/settings.json"));
    }

    static Path defaultConfigDir() {
        var env = System.getenv("CLAUDE_CONFIG_DIR");
        return env != null && !env.isBlank() ? Path.of(env) : Path.of(System.getProperty("user.home"), ".claude");
    }
}
