package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.core.RefKind;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Option;

/** Options every query verb shares. */
public final class Common {

    @Option(names = { "-p", "--project" }, paramLabel = "<dir>",
            description = "Any path inside the project. Defaults to the working directory; the reactor root is found by walking up.")
    Path project;

    @Option(names = { "-T", "--target" }, paramLabel = "<target>",
            description = { "A library you have not opened, instead of a local project. One of:",
                "  groupId:artifactId:version  from ~/.m2 or the gradle cache (sources jar if present, else the jar's class files)",
                "  a git or GitHub URL         shallow-cloned and cached",
                "  a directory                 same as --project; maven or gradle, detected",
                "Not indexed - read on demand, since nobody edits a dependency. Best with tree and api." })
    String target;

    @Option(names = "--build", paramLabel = "<maven|gradle>",
            description = "Which build file to believe when a directory carries both. Default maven.")
    public void build(littlejlib.jacli.proj.BuildKind kind) { littlejlib.jacli.proj.Builds.prefer(kind); }

    @Option(names = "--private", description = "Include package-private and private members.")
    boolean includeNonPublic;

    @Option(names = "--code", description = "Also print each declaration's source, the way symbols -c did.")
    boolean withCode;

    @Option(names = { "-l", "--limit" }, description = "Maximum hits to print. Default ${DEFAULT-VALUE}.")
    int limit = Fmt.DEFAULT_LIMIT;

    @Option(names = { "-d", "--depth" },
            description = "How deep to walk: callers/callees default 1, deps 3, releasable 6.")
    int depth;

    /** 0 means the user did not say, so each verb applies the depth that suits it. */
    public int depthOr(int fallback) { return depth > 0 ? depth : fallback; }

    @Option(names = "--kind", split = ",", paramLabel = "<kind>",
            description = "Keep only these reference kinds: ${COMPLETION-CANDIDATES}.")
    Set<RefKind> kinds = new LinkedHashSet<>();

    @Option(names = "--overrides",
            description = "Also follow the override/implementation chain, as an IDE's 'including overrides' does.")
    boolean overrides;

    @Option(names = "--all", description = "When a spec is ambiguous, report every match instead of listing candidates.")
    boolean all;

    @Option(names = "--field",
            description = "Only the field of that name. A field has no parenthesised form, so this is how to pick it over a same-named method.")
    boolean fieldOnly;

    @Option(names = "--method", description = "Only methods and constructors of that name.")
    boolean methodOnly;

    @Option(names = { "-t", "--tests" }, description = "Include src/test/java.")
    boolean tests;

    @Option(names = "--direct",
            description = "Bypass the daemon and index; analyse in this process. Slower per call, useful for debugging.")
    boolean direct;

    @Option(names = "--reindex", description = "Discard this project's index and rebuild it from scratch.")
    boolean reindex;

    @Option(names = "--scope", paramLabel = "<group>",
            description = "Answer across every project in a declared group. See jacli scope.")
    String scope;

    @Option(names = "--with", paramLabel = "<dir>", split = ",",
            description = "Answer across these sibling projects as well, for this call only.")
    List<Path> with = new ArrayList<>();

    @Option(names = "--syntactic",
            description = "Skip attribution entirely: parse only. Fast, needs no classpath, resolves nothing.")
    boolean syntactic;

    @Option(names = { "-v", "--verbose" }, description = "Diagnostics on stderr.")
    boolean verbose;

    @Option(names = { "-o", "--out" }, paramLabel = "<file>",
            description = "Write the result here instead of stdout, and print only the path. For an API surface too large to want inline.")
    Path out;

    /** Prints, or writes to {@code --out} and reports where, so a large surface need not cross the wire. */
    public void emit(String text) {
        if (out == null) {
            System.out.print(text);
            return;
        }
        var dest = littlejlib.jacli.proj.Cwd.resolve(out);
        try {
            if (dest.getParent() != null) Files.createDirectories(dest.getParent());
            Files.writeString(dest, text, StandardCharsets.UTF_8);
            System.out.println("# written to " + dest + "  (" + text.length() + " bytes, "
                    + text.lines().count() + " lines)");
        } catch (IOException e) {
            System.err.println("jacli: could not write " + dest + ": " + e.getMessage());
            System.out.print(text);
        }
    }

    public Path where() {
        return project != null ? littlejlib.jacli.proj.Cwd.resolve(project) : littlejlib.jacli.proj.Cwd.get();
    }

    public Opts opts() {
        return Opts.defaults().withLimit(limit).withDepth(depth).withKinds(Set.copyOf(kinds))
                .withOverrides(overrides).withAll(all).withNonPublic(includeNonPublic).withCode(withCode).withMember(
                        fieldOnly ? Opts.Member.FIELD : methodOnly ? Opts.Member.METHOD : Opts.Member.ANY);
    }

    public Consumer<String> log() {
        return verbose ? s -> System.err.println("jacli: " + s) : s -> { };
    }

    public Store store() {
        if (target != null) return Engine.target(target, includeNonPublic, log());
        if (direct || syntactic) return Engine.direct(where(), tests, syntactic, log());
        var shared = littlejlib.jacli.index.ServerContext.index();
        var index = shared != null ? shared
                : new littlejlib.jacli.index.ProjectIndex(littlejlib.jacli.index.Db.get().db());
        var roots = scopeRoots(index);
        if (roots.size() > 1) return index.ensureScope(roots, scope != null ? scope : "ad-hoc", tests, reindex, log());
        return index.ensure(where(), tests, reindex, log());
    }

    List<Path> scopeRoots(littlejlib.jacli.index.ProjectIndex index) {
        var roots = new ArrayList<Path>();
        roots.add(where());
        if (scope != null) index.groupRoots(scope).forEach(r -> {
            if (!roots.contains(r)) roots.add(r);
        });
        with.stream().map(littlejlib.jacli.proj.Cwd::resolve).forEach(r -> {
            if (!roots.contains(r)) roots.add(r);
        });
        return roots;
    }

    /**
     * When a project belongs to a group but the query was not widened, say so. A capability nothing
     * mentions is a capability nobody uses - that was the whole lesson of refs-across-a-seam.
     */
    public String scopeHint(Store store) {
        if (scope != null || !with.isEmpty() || target != null || direct) return "";
        var shared = littlejlib.jacli.index.ServerContext.index();
        if (shared == null) return "";
        var group = shared.groupOf(where());
        if (group == null) return "";
        var size = shared.groupRoots(group).size();
        return "# this project is part of scope '" + group + "' (" + size + " projects); "
                + "add --scope " + group + " to answer across all of them\n";
    }

    /** True when this process owns the database and must therefore close it when the command ends. */
    public boolean ownsDb() {
        return target == null && !direct && !syntactic
                && littlejlib.jacli.index.ServerContext.index() == null;
    }
}
