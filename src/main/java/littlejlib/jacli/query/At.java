package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/**
 * What is at this file and line.
 * <p>
 * Every other verb here starts from a symbol, and that is not where a caller actually starts. It
 * starts from a grep hit, a stack frame, a compiler error or a line in a diff — a file and a number.
 * Turning that into a symbol meant opening the file and reading upwards for the enclosing method,
 * which is the manual step this whole tool exists to remove, and the reason the observed usage
 * pattern was {@code grep -n} followed by {@code sed -n 'A,Bp'} rather than anything compiler-aware.
 * <p>
 * So this is the entry ramp: give it where you are, and it names the declaration you are inside and
 * every symbol referenced on that line, each as a spec that can be pasted into the next verb.
 */
public final class At {

    private At() { }

    public static final int NEARBY_LINES = 2;

    public static Answer at(Store store, String spec, Opts opts) {
        var loc = Loc.parse(spec);
        if (loc == null) return new Answer(Answer.USAGE,
                "# give a file and a line: jacli at src/main/java/x/Foo.java:42\n"
                        + "# a bare file name works too, and lists what it declares\n");

        var inFile = store.allDecls().filter(d -> loc.matchesFile(d.file())).toList();
        if (inFile.isEmpty()) return notFound(store, loc);
        if (loc.line() <= 0) return wholeFile(store, loc, inFile);

        var enclosing = inFile.stream()
                .filter(d -> d.line() <= loc.line() && loc.line() <= d.endLine())
                .sorted(Comparator.comparingInt(d -> d.endLine() - d.line()))
                .toList();
        var sb = new StringBuilder("# at " + loc.file() + ":" + loc.line() + '\n' + Fmt.context(store.meta()));
        if (enclosing.isEmpty()) {
            sb.append("# no declaration spans that line - it is between members, or past the end of the file.\n");
            sb.append("# what this file declares:\n").append(Fmt.decls(store.root(), inFile));
            return Answer.nothing(sb.toString());
        }

        var innermost = enclosing.getFirst();
        sb.append("\n# innermost declaration containing that line\n");
        for (var d : enclosing) {
            sb.append("  ").append(Fmt.precise(d)).append('\n')
                    .append("      ").append(d.signature())
                    .append("   ").append(Src.abs(store.root(), d.file()))
                    .append(':').append(d.line()).append('-').append(d.endLine()).append('\n');
        }
        sb.append(referenced(store, innermost, loc.line()));
        sb.append("\n# next:  jacli src ").append(Fmt.precise(innermost)).append("        the body\n")
                .append("#        jacli callers ").append(Fmt.precise(innermost))
                .append("    who reaches this line\n");
        return Answer.ok(sb.toString());
    }

    /**
     * The symbols used ON that line, with the key each resolved to. This is the half a text search
     * cannot do: the line says {@code g.greet("y")} and the answer says which {@code greet}, in which
     * type, ready to paste into {@code refs}.
     */
    static String referenced(Store store, Decl enclosing, int line) {
        var here = store.refsFrom(enclosing.key()).stream()
                .filter(o -> Math.abs(o.line() - line) <= NEARBY_LINES)
                .sorted(Comparator.comparingInt(Occurrence::line).thenComparingInt(Occurrence::col))
                .toList();
        if (here.isEmpty()) return "";
        var sb = new StringBuilder("\n# symbols referenced on and around that line\n");
        var seen = new HashSet<String>();
        for (var o : here) {
            if (!seen.add(o.targetKey())) continue;
            sb.append(String.format("  %4d:%-3d %-11s %s%n", o.line(), o.col(), o.kind(), o.targetKey()));
        }
        return sb.toString();
    }

    static Answer wholeFile(Store store, Loc loc, List<Decl> inFile) {
        var sb = new StringBuilder("# " + loc.file() + '\n' + Fmt.context(store.meta())
                + "# " + Fmt.plural(inFile.size(), "declaration", "declarations") + '\n');
        sb.append(Fmt.decls(store.root(), inFile));
        return Answer.ok(sb.toString());
    }

    static Answer notFound(Store store, Loc loc) {
        return Answer.nothing("# no indexed java file matches '" + loc.file() + "'\n"
                + Fmt.context(store.meta())
                + "# the path is matched by suffix, so a relative path from the project root works,\n"
                + "# and so does the absolute one. Non-java files are not indexed.\n");
    }

    /**
     * A file with an optional line and column. Split from the RIGHT, and only where the tail is
     * numeric, because a Windows path opens with {@code C:} and splitting from the left would take the
     * drive letter for a filename and the rest for a line number.
     */
    public record Loc(String file, int line) {

        public static Loc parse(String spec) {
            if (spec == null || spec.isBlank()) return null;
            var s = spec.trim().replace('\\', '/');
            var line = 0;
            for (var pass = 0; pass < 2; pass++) {
                var cut = s.lastIndexOf(':');
                if (cut <= 0) break;
                var tail = s.substring(cut + 1);
                if (tail.isEmpty() || !tail.chars().allMatch(Character::isDigit)) break;
                /* first numeric tail is the column when a second follows, so keep the outer one */
                line = Integer.parseInt(tail);
                s = s.substring(0, cut);
            }
            return s.isBlank() ? null : new Loc(s, line);
        }

        boolean matchesFile(String indexed) {
            if (indexed == null) return false;
            var a = indexed.replace('\\', '/');
            var b = file;
            return a.equals(b) || a.endsWith("/" + b) || b.endsWith("/" + a) || last(a).equals(last(b));
        }

        static String last(String p) {
            var i = p.lastIndexOf('/');
            return i < 0 ? p : p.substring(i + 1);
        }
    }
}
