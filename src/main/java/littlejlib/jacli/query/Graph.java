package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/** Call hierarchy, type hierarchy, and dead public API — the questions grep has no form for. */
public final class Graph {

    private Graph() { }

    public static final int MAX_DEPTH = 12;

    public static Answer callers(Store store, String specText, Opts opts) {
        return hierarchy(store, specText, opts, true);
    }

    public static Answer callees(Store store, String specText, Opts opts) {
        return hierarchy(store, specText, opts, false);
    }

    static Answer hierarchy(Store store, String specText, Opts opts, boolean up) {
        if (!store.meta().hasReferences()) return Verbs.needsReferences(store, up ? "callers" : "callees");
        var spec = Spec.parse(specText);
        var target = SymbolTarget.resolve(store, spec, opts);
        if (target.isEmpty()) return Verbs.unknown(store, spec, up ? "callers" : "callees");
        if (target.isAmbiguous() && !opts.all())
            return Answer.ambiguous(Fmt.candidates(store.root(), Verbs.candidateDecls(store, target), specText));

        var depth = Math.max(1, Math.min(opts.depth(), MAX_DEPTH));
        var sb = new StringBuilder(Fmt.meta((up ? "callers of " : "callees of ") + Verbs.subject(target, specText),
                target.primary(), store.root(), store.meta(), "call hierarchy to depth " + depth));
        var seen = new HashSet<String>(target.keys());
        var count = new int[1];
        for (var k : target.keys()) walk(store, k, up, 1, depth, seen, sb, opts, count);
        if (count[0] == 0) sb.append(up
                ? "# nothing in this project calls it.\n"
                : "# it calls nothing that resolves inside this project.\n");
        return Answer.ok(sb.toString());
    }

    static void walk(Store store, String key, boolean up, int level, int maxDepth,
                     Set<String> seen, StringBuilder sb, Opts opts, int[] count) {
        if (level > maxDepth) return;
        for (var next : step(store, key, up)) {
            if (count[0] >= opts.limit()) return;
            var fresh = seen.add(next.key());
            count[0]++;
            sb.append("  ".repeat(level)).append(next.label(store)).append(fresh ? "" : "   (already shown)").append('\n');
            if (fresh) walk(store, next.key(), up, level + 1, maxDepth, seen, sb, opts, count);
        }
    }

    static List<Edge> step(Store store, String key, boolean up) {
        var out = new LinkedHashMap<String, Edge>();
        if (up) {
            for (var o : store.refsTo(key))
                if (o.kind().isInvocation() && o.enclosingKey() != null)
                    out.putIfAbsent(o.enclosingKey(), new Edge(o.enclosingKey(), o.file(), o.line()));
        } else {
            for (var o : store.refsFrom(key))
                if (o.kind().isInvocation() && !o.targetKey().equals(key))
                    out.putIfAbsent(o.targetKey(), new Edge(o.targetKey(), o.file(), o.line()));
        }
        return List.copyOf(out.values());
    }

    public static Answer impls(Store store, String specText, Opts opts) {
        var spec = Spec.parse(specText);
        var decls = store.declsMatching(spec, opts);
        var keys = decls.isEmpty()
                ? store.targetKeysByName(spec.searchName()).stream().filter(spec::matchesKey).toList()
                : decls.stream().map(Decl::key).toList();
        if (keys.isEmpty()) return Verbs.unknown(store, spec, "impls");

        var all = new LinkedHashMap<String, Decl>();
        for (var k : keys) for (var d : store.declsWithSuper(k)) all.putIfAbsent(d.key(), d);
        var sb = new StringBuilder(Fmt.meta("implementations and subtypes of " + String.join(", ", keys),
                decls.isEmpty() ? null : decls.getFirst(), store.root(), store.meta(),
                Fmt.plural(all.size(), "subtype", "subtypes") + " transitively"));
        if (all.isEmpty()) {
            sb.append("# nothing in this project extends or implements it.\n");
            return store.meta().tier().isExact() ? Answer.nothing(sb.toString()) : Answer.degradedEmpty(sb.toString());
        }
        var printed = new HashSet<String>(keys);
        var count = new int[1];
        for (var k : keys) directTree(store, k, 1, printed, sb, opts, count);
        detached(store, all, printed, sb, opts, count);
        return Answer.ok(sb.toString());
    }

    /** The hierarchy as declared, following immediate supertypes so the shape is real. */
    static void directTree(Store store, String key, int level, Set<String> printed,
                           StringBuilder sb, Opts opts, int[] count) {
        if (level > MAX_DEPTH) return;
        for (var d : store.directSubtypesOf(key)) {
            if (count[0] >= opts.limit()) return;
            if (!printed.add(d.key())) continue;
            count[0]++;
            sb.append("  ".repeat(level)).append(line(store, d)).append('\n');
            directTree(store, d.key(), level + 1, printed, sb, opts, count);
        }
    }

    /**
     * Subtypes reached only transitively, because a type in the chain is declared outside this index —
     * luvml's implementations reach luvx.Frag_I through luvx-base. They are real subtypes, so they are
     * reported, flagged rather than silently dropped.
     */
    static void detached(Store store, Map<String, Decl> all, Set<String> printed,
                         StringBuilder sb, Opts opts, int[] count) {
        var rest = all.values().stream().filter(d -> !printed.contains(d.key()))
                .sorted(Comparator.comparing(Decl::key)).toList();
        if (rest.isEmpty()) return;
        sb.append("# ").append(rest.size()).append(" reached through a supertype declared outside this index")
                .append(" - real subtypes, but their intermediate parent is not here to nest them under\n");
        for (var d : rest) {
            if (count[0] >= opts.limit()) return;
            count[0]++;
            sb.append("  ").append(line(store, d)).append('\n');
        }
    }

    static String line(Store store, Decl d) {
        return d.signature() + "   " + Src.abs(store.root(), d.file()) + ":" + d.line();
    }

    /**
     * Public API nothing in this project references. Overriders, constructors and {@code main} are
     * excluded because they are called from outside by construction, and the result is only
     * meaningful for a library whose consumers are all inside the index.
     */
    public static Answer unused(Store store, Opts opts) {
        if (!store.meta().hasReferences()) return Verbs.needsReferences(store, "unused");
        var m = store.meta();
        var sb = new StringBuilder(Fmt.meta("public API with no references inside this project", null,
                store.root(), m, "candidates only"));
        sb.append("# Note: a library's public API is called by its consumers, which are outside this index.\n");
        var referenced = store.referencedKeys();
        /* Under --scope, widen WHERE references are looked for but not WHAT is audited. Auditing every
         * project in the scope at once just adds each sibling's own unused API to the pile; the useful
         * question is this project's public API, checked against the whole scope's usage. */
        var here = store.root().toString();
        var found = store.allDecls()
                .filter(d -> opts.all() || d.file() == null || d.file().startsWith(here))
                .filter(Decl::isPublicApi)
                .filter(d -> d.kind() != DeclKind.CONSTRUCTOR)
                .filter(d -> d.superKeys().isEmpty())
                .filter(d -> !"main".equals(d.name()))
                .filter(d -> !referenced.contains(d.key()))
                .sorted(Comparator.comparing(Decl::file).thenComparingInt(Decl::line))
                .limit(opts.limit())
                .toList();
        sb.append(Fmt.decls(store.root(), found));
        sb.append("# ").append(found.size()).append(" candidates");
        sb.append(opts.all() ? " across the whole scope\n"
                : " declared in this project, checked against every reference in scope. --all audits the whole scope.\n");
        return found.isEmpty() ? Answer.nothing(sb.toString()) : Answer.ok(sb.toString());
    }
}
