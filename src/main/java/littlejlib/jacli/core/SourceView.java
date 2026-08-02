package littlejlib.jacli.core;

import module java.base;

/**
 * Source slicing for the {@code src} verb. Because every declaration carries its span in the index,
 * showing a method — or every method named in a stack trace — costs one file read and no javac.
 * The javadoc above a declaration is found by walking back over the comment block rather than by
 * storing a second position, which keeps the index smaller and cannot drift out of step.
 */
public final class SourceView {

    private SourceView() { }

    public static List<String> read(Path file) {
        try {
            return java.nio.file.Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (java.nio.charset.CharacterCodingException e) {
            return readLossy(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> readLossy(Path file) {
        try {
            return List.of(new String(java.nio.file.Files.readAllBytes(file), StandardCharsets.UTF_8).split("\r?\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 1-based, inclusive, clamped. */
    public static String slice(List<String> lines, int from, int to, boolean numbered) {
        var a = Math.max(1, Math.min(from, lines.size()));
        var b = Math.max(a, Math.min(to, lines.size()));
        var sb = new StringBuilder();
        for (var i = a; i <= b; i++) {
            if (numbered) sb.append(i).append('\t');
            sb.append(lines.get(i - 1)).append('\n');
        }
        return sb.toString();
    }

    /** The line the annotations and javadoc or comment block above {@code declLine} start on. */
    public static int docStart(List<String> lines, int declLine) {
        var top = declLine;
        while (top > 1 && isAnnotationOrBlank(at(lines, top - 1))) top--;
        var above = top - 1;
        if (above >= 1) {
            var t = at(lines, above).trim();
            if (t.endsWith("*/")) {
                for (var j = above; j >= 1; j--) if (at(lines, j).trim().startsWith("/*")) return j;
                return above;
            }
            if (t.startsWith("//")) {
                var s = above;
                while (s > 1 && at(lines, s - 1).trim().startsWith("//")) s--;
                return s;
            }
        }
        while (top < declLine && at(lines, top).isBlank()) top++;
        return top;
    }

    static String at(List<String> lines, int line) {
        return line >= 1 && line <= lines.size() ? lines.get(line - 1) : "";
    }

    static boolean isAnnotationOrBlank(String line) {
        var t = line.trim();
        return t.startsWith("@") || t.isEmpty();
    }

    public static String of(Path root, Decl d, boolean withDoc, boolean numbered) {
        var file = Src.abs(root, d.file());
        var lines = read(file);
        var from = withDoc ? docStart(lines, d.line()) : d.line();
        return slice(lines, from, d.endLine(), numbered);
    }

    /** The single line an occurrence sits on, trimmed, for one-line context in {@code refs} output. */
    public static String lineAt(Path root, String relFile, int line, Map<String, List<String>> cache) {
        var lines = cache.computeIfAbsent(relFile, f -> read(Src.abs(root, f)));
        return line >= 1 && line <= lines.size() ? lines.get(line - 1).trim() : "";
    }
}
