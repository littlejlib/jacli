package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;

/** Path handling shared by the engine. Relative paths are stored with '/' so the index stays portable. */
public final class Src {

    private Src() { }

    public static String rel(Path root, JavaFileObject file) {
        return rel(root, Path.of(file.toUri()));
    }

    public static String rel(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.toString().replace('\\', '/');
        }
    }

    public static Path abs(Path root, String rel) {
        return root.resolve(rel.replace('/', java.io.File.separatorChar));
    }

    public static List<Path> javaFiles(List<Path> sourceRoots) {
        var out = new ArrayList<Path>();
        for (var r : sourceRoots) {
            if (!java.nio.file.Files.isDirectory(r)) continue;
            try (var w = java.nio.file.Files.walk(r)) {
                w.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    /** Word-boundary containment, the cheap shortlist that keeps javac off files that cannot match. */
    public static boolean containsWord(Path file, String word) {
        try {
            return containsWord(java.nio.file.Files.readString(file), word);
        } catch (IOException e) {
            return true;
        }
    }

    public static boolean containsWord(String s, String word) {
        for (var i = s.indexOf(word); i >= 0; i = s.indexOf(word, i + 1)) {
            var okBefore = i == 0 || !Character.isJavaIdentifierPart(s.charAt(i - 1));
            var end = i + word.length();
            var okAfter = end >= s.length() || !Character.isJavaIdentifierPart(s.charAt(end));
            if (okBefore && okAfter) return true;
        }
        return false;
    }
}
