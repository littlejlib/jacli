package littlejlib.jacli.claude;

import module java.base;
import com.google.gson.*;

/**
 * What the installed hooks decide, in java so a machine needs neither jq nor this repository's scripts.
 * The bash in hooks.json only prefilters on a word, so the JVM starts for a candidate and not for every
 * command. Returns the hook's JSON answer, or empty for silence.
 */
final class Hooks {

    private Hooks() { }

    static final Pattern JAR_SEARCH = Pattern.compile(
            "\\bunzip\\b[^|;]*(\\.m2|gradle[/\\\\]caches)[^|;]*\\.jar|\\bfind\\b\\s+[/~]\\s+[^|;]*(-i?name|\\.jar)");
    static final Pattern JAVAP = Pattern.compile("(^|[^a-zA-Z0-9_-])javap(\\s|$)|\\bunzip\\b[^|;]*\\.jar");
    static final Pattern SYMBOLISH = Pattern.compile("(implements|extends|interface|class|record|new)\\s+[A-Z]");
    static final Pattern CAMEL = Pattern.compile("[A-Z][a-z]");
    static final List<String> BUILD_FILES = List.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts");

    static Optional<String> answer(String event, String stdin) {
        var in = stdin.isBlank() ? new JsonObject() : JsonParser.parseString(stdin).getAsJsonObject();
        var input = in.has("tool_input") ? in.getAsJsonObject("tool_input") : new JsonObject();
        return switch (event) {
            case "pre-bash" -> preBash(str(input, "command"));
            case "pre-grep" -> preGrep(str(input, "pattern"), str(input, "glob") + " " + str(input, "type") + " " + str(input, "path"));
            case "user-prompt" -> userPrompt(projectDir(in));
            default -> throw new IllegalArgumentException("unknown hook event " + event);
        };
    }

    static Optional<String> preBash(String command) {
        if (JAR_SEARCH.matcher(command).find()) return Optional.of(deny(Kit.text("hook-jar-search.txt")));
        if (JAVAP.matcher(command).find()) return Optional.of(context("PreToolUse", Kit.text("hook-javap.txt")));
        return Optional.empty();
    }

    static Optional<String> preGrep(String pattern, String where) {
        var shape = SYMBOLISH.matcher(pattern).find() || pattern.contains("(");
        var java = where.toLowerCase().contains("java") || CAMEL.matcher(pattern).find();
        return shape && java ? Optional.of(context("PreToolUse", Kit.text("hook-grep.txt"))) : Optional.empty();
    }

    static Optional<String> userPrompt(Path dir) {
        var java = BUILD_FILES.stream().anyMatch(f -> Files.exists(dir.resolve(f)));
        return java ? Optional.of(context("UserPromptSubmit", Kit.text("hook-user-prompt.txt"))) : Optional.empty();
    }

    static Path projectDir(JsonObject in) {
        var env = System.getenv("CLAUDE_PROJECT_DIR");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of(in.has("cwd") ? in.get("cwd").getAsString() : "");
    }

    static String context(String event, String text) {
        var out = new JsonObject();
        out.addProperty("hookEventName", event);
        out.addProperty("additionalContext", text.strip());
        return wrap(out);
    }

    static String deny(String reason) {
        var out = new JsonObject();
        out.addProperty("hookEventName", "PreToolUse");
        out.addProperty("permissionDecision", "deny");
        out.addProperty("permissionDecisionReason", reason.strip());
        return wrap(out);
    }

    static String wrap(JsonObject specific) {
        var root = new JsonObject();
        root.add("hookSpecificOutput", specific);
        return root.toString();
    }

    static String str(JsonObject o, String key) {
        var e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }
}
