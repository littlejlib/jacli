package littlejlib.jacli.core;

import module java.base;

/** First javadoc sentence, flattened. Costs a few tokens and often saves the agent reading the file. */
public final class Doc {

    private Doc() { }

    public static final int MAX = 240;

    public static String firstSentence(String javadoc) {
        if (javadoc == null || javadoc.isBlank()) return null;
        var text = javadoc.replaceAll("(?s)\\{@\\w+\\s+([^}]*)\\}", "$1")
                .replaceAll("(?s)<[^>]+>", "")
                .replaceAll("(?m)^\\s*@.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty()) return null;
        var end = endOfSentence(text);
        var out = end < 0 ? text : text.substring(0, end + 1);
        return out.length() > MAX ? out.substring(0, MAX - 1) + "..." : out;
    }

    static int endOfSentence(String s) {
        for (var i = 0; i < s.length() - 1; i++)
            if (s.charAt(i) == '.' && Character.isWhitespace(s.charAt(i + 1))) return i;
        return s.endsWith(".") ? s.length() - 1 : -1;
    }
}
