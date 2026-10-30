package littlejlib.jacli.claude;

import module java.base;

/** The one import line in CLAUDE.md, added and removed without touching any other line or its line endings. */
final class Memory {

    private Memory() { }

    static boolean has(String text, String line) { return text.lines().anyMatch(l -> l.strip().equals(line)); }

    static String with(String text, String line) {
        if (has(text, line)) return text;
        var nl = text.contains("\r\n") ? "\r\n" : "\n";
        return text + (text.isEmpty() || text.endsWith("\n") ? "" : nl) + line + nl;
    }

    static String without(String text, String line) {
        return text.replaceAll("(?m)^[ \\t]*" + Pattern.quote(line) + "[ \\t]*(\\r?\\n|\\z)", "");
    }
}
