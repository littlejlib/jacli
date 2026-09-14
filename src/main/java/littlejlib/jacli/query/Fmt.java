package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/**
 * Output shaping. Every hit carries an absolute {@code file:line:col} so the agent can Read straight
 * to it, meta lines are prefixed {@code #} so they are trivially skippable, and the source line is
 * echoed because it is usually the whole answer and costs less than a follow-up file read.
 */
public final class Fmt {

    private Fmt() { }

    public static final int SOURCE_WIDTH = 130, DEFAULT_LIMIT = 400;

    /**
     * Whether the project-wide half of the banner has already been printed in this answer. One call
     * may now carry several symbols, and the tier line and its warnings describe the INDEX rather than
     * the symbol — repeating them per symbol would triple the size of a six-symbol answer while saying
     * nothing new. Symbol-specific lines are always printed; only the shared context is folded.
     * <p>
     * A ThreadLocal rather than a static because the daemon serves on a worker thread; it is reset by
     * {@link #beginAnswer()} at the top of every command so a leaked flag cannot silence the banner of
     * an unrelated later request.
     */
    static final ThreadLocal<Boolean> CONTEXT_SHOWN = ThreadLocal.withInitial(() -> false);

    /** Called once per command invocation, before any answer is rendered. */
    public static void beginAnswer() { CONTEXT_SHOWN.set(false); }

    public static String meta(String subject, Decl target, Path root, Meta m, String countLine) {
        var sb = new StringBuilder();
        sb.append("# ").append(subject);
        if (target != null) sb.append("  -  ").append(target.signature());
        sb.append('\n');
        if (target != null)
            sb.append("# declared: ").append(Src.abs(root, target.file())).append(':').append(target.line()).append('\n');
        if (CONTEXT_SHOWN.get()) {
            sb.append("# ").append(countLine).append('\n');
            return sb.toString();
        }
        sb.append("# ").append(countLine).append("  -  ").append(m.line()).append('\n');
        sb.append(warnings(m));
        CONTEXT_SHOWN.set(true);
        return sb.toString();
    }

    /**
     * The project-wide context on its own, for the answers that do not go through {@link #meta} —
     * chiefly the "no symbol matched" reply, which must fold the same way when it is one line of a
     * many-symbol answer rather than the whole of a single-symbol one.
     */
    public static String context(Meta m) {
        if (CONTEXT_SHOWN.get()) return "";
        CONTEXT_SHOWN.set(true);
        return "# " + m.line() + '\n' + warnings(m);
    }

    static String warnings(Meta m) {
        var sb = new StringBuilder();
        for (var w : m.warnings()) sb.append("# ! ").append(w).append('\n');
        return sb.toString();
    }

    public static String occurrences(Path root, List<Occurrence> occ, int limit) {
        var sorted = occ.stream()
                .sorted(Comparator.comparing(Occurrence::file).thenComparingInt(Occurrence::line).thenComparingInt(Occurrence::col))
                .toList();
        var shown = sorted.size() > limit ? sorted.subList(0, limit) : sorted;
        var cache = new HashMap<String, List<String>>();
        var sb = new StringBuilder();
        var currentFile = (String) null;
        for (var o : shown) {
            if (!o.file().equals(currentFile)) {
                currentFile = o.file();
                sb.append('\n').append(Src.abs(root, currentFile)).append('\n');
            }
            sb.append(String.format("  %4d:%-3d %-11s %-24s %s%n", o.line(), o.col(), o.kind(),
                    SymbolKey.compactForm(o.enclosingKey()), source(root, o, cache)));
        }
        if (sorted.size() > shown.size())
            sb.append("\n# ").append(sorted.size() - shown.size())
                    .append(" more not shown - narrow the query or raise --limit\n");
        return sb.toString();
    }

    static String source(Path root, Occurrence o, Map<String, List<String>> cache) {
        try {
            var line = SourceView.lineAt(root, o.file(), o.line(), cache);
            return line.length() > SOURCE_WIDTH ? line.substring(0, SOURCE_WIDTH - 1) + "..." : line;
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String decls(Path root, List<Decl> ds) {
        var sb = new StringBuilder();
        for (var d : ds.stream().sorted(Comparator.comparing(Decl::file).thenComparingInt(Decl::line)).toList())
            sb.append("  ").append(Src.abs(root, d.file())).append(':').append(d.line()).append(':').append(d.col())
                    .append("  ").append(d.signature())
                    .append(d.doc() == null ? "" : "\n      " + d.doc()).append('\n');
        return sb.toString();
    }

    /**
     * Every remedy offered here must be usable. A field's precise spec is byte-identical to a bare
     * member spec, because only a method carries a parameter list — so when a field and a method share
     * a name, "re-run with one of these" would hand back the input that just failed. Name the kind
     * flags in that case, and always name {@code --all}.
     */
    public static String candidates(Path root, List<Decl> ds, String spec) {
        var sorted = ds.stream().sorted(Comparator.comparing(Decl::key)).toList();
        var hasField = sorted.stream().anyMatch(d -> !d.kind().isExecutable() && !d.kind().isType());
        var hasMethod = sorted.stream().anyMatch(d -> d.kind().isExecutable());
        var sb = new StringBuilder("# '" + spec + "' matches " + plural(sorted.size(), "declaration", "declarations")
                + " - narrow it with one of these\n");
        for (var d : sorted) {
            var kind = d.kind().isExecutable() ? "" : "  [" + d.kind().name().toLowerCase(Locale.ROOT) + "]";
            sb.append("  ").append(precise(d)).append(kind).append("\n      ").append(d.signature())
                    .append("   ").append(Src.abs(root, d.file())).append(':').append(d.line()).append('\n');
        }
        if (hasField && hasMethod)
            sb.append("# a field and a method share this name, and a field has no parenthesised form, so add\n")
                    .append("#   --field    for the field\n")
                    .append("#   --method   for the method, or paste its full signature above\n");
        sb.append("# or --all to report every one of them together\n");
        return sb.toString();
    }

    /** The unambiguous spec for a declaration, ready to paste back as an argument. */
    public static String precise(Decl d) {
        if (d.kind().isType()) return d.key();
        if (!d.kind().isExecutable()) return d.key();
        return Keys.spelling(d.key());
    }

    public static String kv(String key, Object value) { return "# " + key + ": " + value + "\n"; }

    public static String plural(long n, String one, String many) { return n + " " + (n == 1 ? one : many); }
}
