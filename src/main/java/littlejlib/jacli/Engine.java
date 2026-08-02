package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.*;
import littlejlib.jacli.query.*;

/**
 * Builds a {@link Store} for a project without any daemon or database — one javac pass into memory.
 * This is what {@code --direct} uses, and what the verbs are verified against, so a bug can always
 * be localised to either the engine or the index rather than to both at once.
 */
public final class Engine {

    private Engine() { }

    public static Store direct(Path where, boolean includeTests, boolean syntacticOnly, Consumer<String> log) {
        var project = Projects.open(where, includeTests, false, log);
        if (project.javaFiles().isEmpty())
            throw new IllegalStateException("no java sources found under " + project.root()
                    + " (looked in " + project.sourceRoots() + ")");
        try (var analyzer = new Analyzer(project.root(), project.classpath().classpath(), List.of())) {
            var analysis = analyzer.analyze(project.javaFiles(), syntacticOnly);
            log.accept(analysis.tier() + " " + analysis.timings() + " for " + analysis.fileCount() + " files");
            return new MemStore(project.root(), meta(project, analysis, true, ""),
                    analysis.decls(), analysis.refs(), analysis.supers());
        }
    }

    /**
     * A store for a target that is not a local source tree: maven coordinates or a git URL. Never
     * indexed — these are read once, on demand, from a disk cache of the extracted sources or
     * straight from the jar's class files. The index exists to make repeated questions about code
     * you are editing cheap, and nobody edits a dependency.
     */
    public static Store target(String spec, boolean includeNonPublic, Consumer<String> log) {
        var t = Targets.resolve(spec, log);
        if (t.isLocal()) return direct(t.root(), false, false, log);
        log.accept("target " + t.describe());
        if (t.kind() == Target.Kind.MAVEN_CLASSES) {
            var read = ClassApi.read(t.jar(), includeNonPublic, log);
            return new MemStore(t.root(), targetMeta(t, Tier.RESOLVED, read.decls().size(), read.typeCount(),
                    "read from bytecode: signatures and types are exact, but there are no source line "
                            + "numbers and parameter names are arg0, arg1 unless the jar was built with "
                            + "-parameters. src decompiles a body on request, which is a reconstruction "
                            + "rather than the original. For the real thing: mvn dependency:get "
                            + "-Dartifact=" + t.coords() + ":jar:sources"),
                    read.decls(), List.of(), read.supers());
        }
        var files = Src.javaFiles(t.sourceDirs());
        if (files.isEmpty()) throw new IllegalStateException("no java sources found in " + t.origin());
        try (var analyzer = new Analyzer(t.root(), "", List.of())) {
            var analysis = analyzer.analyze(files, true);
            log.accept(analysis.tier() + " " + analysis.timings() + " for " + files.size() + " files");
            return new MemStore(t.root(), targetMeta(t, analysis.tier(), analysis.decls().size(), files.size(),
                    "parsed without a classpath, so signatures are the spellings in the source and types "
                            + "are not resolved. Enough for an API surface, not for refs or callers."),
                    analysis.decls(), analysis.refs(), analysis.supers());
        }
    }

    static Meta targetMeta(Target t, Tier tier, int declCount, int files, String note) {
        return new Meta(t.coords().toString(), t.root(), tier, files, declCount, 0, 0, 0,
                t.kind().toString(), true, "", List.of(note));
    }

    public static Meta meta(JavaProject p, Analysis a, boolean fresh, String staleness) {
        var notes = new ArrayList<>(p.classpath().notes());
        if (!a.errorLines().isEmpty() && a.tier() != Tier.RESOLVED)
            notes.add("first javac errors: " + String.join(" | ", a.errorLines()));
        return new Meta(p.coords().toString(), p.root(), a.tier(), a.fileCount(), a.decls().size(),
                a.refs().size(), a.errors(), a.unresolved(), p.classpath().source(), fresh, staleness, notes);
    }
}
