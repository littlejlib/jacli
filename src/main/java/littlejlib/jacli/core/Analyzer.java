package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;
import javax.tools.ToolProvider;

/**
 * Drives javac over a set of source files and hands back everything the index needs.
 * <p>
 * The file manager is reused across runs, which is what makes a warm reindex cheap — most of the
 * cost of a cold run is reading the dependency jars, not attributing the project's own sources
 * (measured: 66 files with a small classpath attribute in 870 ms, 488 files with all of JavaFX in
 * 10796 ms).
 * <p>
 * Degrades rather than fails. Attribution errors leave the resolved part exact and mark the rest;
 * if attribution collapses outright, the already-parsed trees are re-walked syntactically. Every
 * result carries the {@link Tier} it was obtained at.
 * <p>
 * Not thread safe: one instance per project, used by one request at a time.
 */
public final class Analyzer implements AutoCloseable {

    public static final int MAX_ERROR_LINES = 12;

    final Path root;
    final List<String> options;
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final StandardJavaFileManager fm;

    public Analyzer(Path root, String classpath, List<String> extraOptions) {
        if (compiler == null) throw new IllegalStateException("no system java compiler on this runtime: "
                + "jacli needs a JDK, not a JRE (java.home=" + System.getProperty("java.home") + ")");
        this.root = root;
        this.fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        var opts = new ArrayList<>(List.of("-classpath", classpath == null ? "" : classpath,
                "-proc:none", "-g", "-nowarn", "-Xmaxerrs", "1000"));
        opts.addAll(extraOptions);
        this.options = List.copyOf(opts);
    }

    public Path root() { return root; }

    public Analysis analyze(List<Path> files) { return analyze(files, false); }

    public Analysis analyze(List<Path> files, boolean syntacticOnly) {
        if (files.isEmpty()) return Analysis.empty(Tier.RESOLVED);
        var diags = new DiagnosticCollector<JavaFileObject>();
        var t0 = System.nanoTime();
        var units = fm.getJavaFileObjectsFromPaths(files);
        var task = (JavacTask) compiler.getTask(null, fm, diags, options, null, units);
        var roots = new ArrayList<CompilationUnitTree>();
        try {
            for (var u : task.parse()) roots.add(u);
        } catch (Throwable t) {
            return new Analysis(Tier.TEXTUAL, List.of(), List.of(), List.of(), files.size(), 1,
                    List.of("parse failed: " + t), 0, ms(t0, System.nanoTime()), 0, 0);
        }
        var t1 = System.nanoTime();
        if (syntacticOnly) return syntactic(task, roots, files.size(), diags, ms(t0, t1), 0, System.nanoTime());

        try {
            task.analyze();
        } catch (OutOfMemoryError e) {
            throw e;
        } catch (Throwable t) {
            var t2 = System.nanoTime();
            return syntactic(task, roots, files.size(), diags, ms(t0, t1), ms(t1, t2), t2);
        }
        var t2 = System.nanoTime();
        var errs = errors(diags);
        var ex = new Extractor(task, root, errs > 0);
        for (var u : roots) ex.scanUnit(u);
        var t3 = System.nanoTime();
        return new Analysis(errs == 0 ? Tier.RESOLVED : Tier.PARTIAL, ex.decls(), ex.refs(), ex.supers(),
                files.size(), errs, errorLines(diags), errs == 0 ? 0 : ex.unresolved(),
                ms(t0, t1), ms(t1, t2), ms(t2, t3));
    }

    Analysis syntactic(JavacTask task, List<CompilationUnitTree> roots, int fileCount,
                       DiagnosticCollector<JavaFileObject> diags, long parseMs, long analyzeMs, long from) {
        var sx = new SyntacticExtractor(task, root);
        for (var u : roots) {
            try {
                sx.scanUnit(u);
            } catch (Throwable t) {
                // one unusable unit must not lose the rest of the project
            }
        }
        return new Analysis(Tier.SYNTACTIC, sx.decls(), sx.refs(), sx.supers(), fileCount, errors(diags),
                errorLines(diags), sx.refs().size(), parseMs, analyzeMs, ms(from, System.nanoTime()));
    }

    static long ms(long a, long b) { return (b - a) / 1_000_000; }

    static int errors(DiagnosticCollector<JavaFileObject> d) {
        return (int) d.getDiagnostics().stream().filter(x -> x.getKind() == Diagnostic.Kind.ERROR).count();
    }

    static List<String> errorLines(DiagnosticCollector<JavaFileObject> d) {
        return d.getDiagnostics().stream()
                .filter(x -> x.getKind() == Diagnostic.Kind.ERROR)
                .limit(MAX_ERROR_LINES)
                .map(x -> (x.getSource() == null ? "?" : Path.of(x.getSource().toUri()).getFileName().toString())
                        + ":" + x.getLineNumber() + " " + flatten(x.getMessage(Locale.ROOT)))
                .toList();
    }

    /** javac wraps symbol/location detail onto further lines; keep every diagnostic to one line. */
    static String flatten(String message) {
        return message == null ? "" : message.replaceAll("\\s*\\R\\s*", " ").trim();
    }

    @Override
    public void close() {
        try {
            fm.close();
        } catch (IOException e) {
            // a file manager that will not close is not worth failing a query over
        }
    }
}
