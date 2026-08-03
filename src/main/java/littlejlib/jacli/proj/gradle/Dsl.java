package littlejlib.jacli.proj.gradle;

import module java.base;

/**
 * Just enough of the groovy/kotlin build DSL to read literals out of it, for the fallback that runs
 * when gradle itself cannot be asked. Comments come out first: a commented-out {@code include} is the
 * classic way to make a textual reader confidently wrong.
 */
public final class Dsl {

    private Dsl() { }

    public static String read(Path file) {
        try {
            return Files.isRegularFile(file) ? strip(Files.readString(file, StandardCharsets.UTF_8)) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Line and block comments removed, string literals left intact so a {@code //} in a URL survives. */
    public static String strip(String src) {
        var out = new StringBuilder(src.length());
        var i = 0;
        while (i < src.length()) {
            var c = src.charAt(i);
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                var end = src.indexOf("*/", i + 2);
                out.append(' ');
                i = end < 0 ? src.length() : end + 2;
            } else if (c == '"' || c == '\'') {
                var end = endOfString(src, i);
                out.append(src, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Index just past the closing quote of the literal starting at {@code start}. */
    public static int endOfString(String s, int start) {
        var q = s.charAt(start);
        var triple = String.valueOf(q).repeat(3);
        if (s.startsWith(triple, start)) {
            var end = s.indexOf(triple, start + 3);
            return end < 0 ? s.length() : end + 3;
        }
        for (var i = start + 1; i < s.length(); i++) {
            var c = s.charAt(i);
            if (c == '\\') i++;
            else if (c == q) return i + 1;
            else if (c == '\n') return i;
        }
        return s.length();
    }

    public static String unquote(String lit) {
        var s = lit;
        for (var q : List.of("\"\"\"", "'''"))
            if (s.length() >= 6 && s.startsWith(q) && s.endsWith(q)) return s.substring(3, s.length() - 3);
        if (s.length() >= 2 && (s.charAt(0) == '"' || s.charAt(0) == '\'') && s.charAt(s.length() - 1) == s.charAt(0))
            s = s.substring(1, s.length() - 1);
        return s.replace("\\\"", "\"").replace("\\'", "'").replace("\\\\", "\\");
    }

    /**
     * The quoted arguments of a call whose name ends at {@code from}. Covers {@code include("a", "b")},
     * groovy's parenthesis-free {@code include 'a', 'b'}, and lists broken over several lines.
     * Stops at the first argument that is not a literal, which is what makes
     * {@code include(projects.core)} yield nothing rather than something invented.
     */
    public static List<String> args(String s, int from) {
        var out = new ArrayList<String>();
        var depth = 0;
        for (var i = from; i < s.length(); ) {
            var c = s.charAt(i);
            if (c == '(') {
                depth++;
                i++;
            } else if (c == ')') {
                if (depth > 0) depth--;
                break;
            } else if (c == ',' || Character.isWhitespace(c)) {
                i++;
            } else if (c == '"' || c == '\'') {
                var end = endOfString(s, i);
                out.add(unquote(s.substring(i, end)));
                i = end;
            } else {
                break;
            }
        }
        return out;
    }

    /** The raw text between a call's parentheses, so non-literal arguments can still be inspected. */
    public static String callText(String s, int from) {
        var i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i >= s.length() || s.charAt(i) != '(') {
            var eol = s.indexOf('\n', i);
            return s.substring(i, eol < 0 ? s.length() : eol);
        }
        var depth = 0;
        for (var j = i; j < s.length(); ) {
            var c = s.charAt(j);
            if (c == '"' || c == '\'') {
                j = endOfString(s, j);
                continue;
            }
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) return s.substring(i + 1, j);
            j++;
        }
        return s.substring(i);
    }

    /** The body of the brace block following {@code from}, or empty when the next token is not one. */
    public static String block(String s, int from) {
        var i = from;
        while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == '(' || s.charAt(i) == ')')) i++;
        if (i >= s.length() || s.charAt(i) != '{') return "";
        var depth = 0;
        for (var j = i; j < s.length(); ) {
            var c = s.charAt(j);
            if (c == '"' || c == '\'') {
                j = endOfString(s, j);
                continue;
            }
            if (c == '{') depth++;
            if (c == '}' && --depth == 0) return s.substring(i + 1, j);
            j++;
        }
        return s.substring(i);
    }

    /** Every string literal in the text, unquoted. */
    public static List<String> literals(String s) {
        var out = new ArrayList<String>();
        for (var i = 0; i < s.length(); i++) {
            var c = s.charAt(i);
            if (c != '"' && c != '\'') continue;
            var end = endOfString(s, i);
            out.add(unquote(s.substring(i, end)));
            i = end - 1;
        }
        return out;
    }

    /** Every offset at which {@code word} appears as a whole identifier. */
    public static List<Integer> occurrences(String s, String word) {
        var out = new ArrayList<Integer>();
        for (var i = s.indexOf(word); i >= 0; i = s.indexOf(word, i + 1)) {
            var before = i == 0 || !Character.isJavaIdentifierPart(s.charAt(i - 1));
            var end = i + word.length();
            var after = end >= s.length() || !Character.isJavaIdentifierPart(s.charAt(end));
            if (before && after && (i == 0 || s.charAt(i - 1) != '.')) out.add(i);
        }
        return out;
    }
}
