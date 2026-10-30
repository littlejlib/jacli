package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

/**
 * Finding out what exists, for when you do not yet know the name — the question that has to be
 * answered before {@code refs} or {@code def} can be asked at all.
 * <p>
 * Answered from the index, so it is fast, it can be filtered by name, and it can report how often
 * each declaration is used, which a pure source renderer has no way of knowing.
 */
public final class Discover {

    private Discover() { }

    public static final int COUNTS_UP_TO = 40, TYPES_PER_LINE = 6;

    /**
     * Nested types are included, shown as {@code Outer.Inner}. {@code tree} is billed as the cheapest
     * complete look at a project, and a public nested class that appears nowhere in it is undiscoverable.
     */
    public static Answer tree(Store store, String prefix) {
        var types = store.allDecls().filter(d -> d.kind().isType())
                .filter(d -> inScope(d, prefix)).toList();
        var byPackage = new TreeMap<String, List<Decl>>();
        for (var d : types) byPackage.computeIfAbsent(packageOfType(d), k -> new ArrayList<>()).add(d);
        var sb = new StringBuilder(Fmt.meta(prefix == null ? "package tree" : "package tree under " + prefix,
                null, store.root(), store.meta(),
                Fmt.plural(byPackage.size(), "package", "packages") + ", "
                        + Fmt.plural(types.size(), "type", "types")));
        byPackage.forEach((pkg, list) -> {
            sb.append('\n').append(pkg.isEmpty() ? "<default>" : pkg).append("  (").append(list.size()).append(")\n");
            var names = list.stream().map(Discover::displayName).sorted().toList();
            for (var i = 0; i < names.size(); i += TYPES_PER_LINE)
                sb.append("  ").append(String.join("  ", names.subList(i, Math.min(names.size(), i + TYPES_PER_LINE))))
                        .append('\n');
        });
        if (types.isEmpty()) sb.append("# nothing indexed under that prefix\n");
        return types.isEmpty() ? Answer.nothing(sb.toString()) : Answer.ok(sb.toString());
    }

    /**
     * Everything callable on a type, its own members plus every supertype's, each labelled with where it
     * came from. For an interface-heavy codebase this is the common case rather than the exotic one:
     * when nearly every type is an {@code _I} interface whose entire API is inherited, "what can I
     * actually call on this" needs the fan-out every single time.
     */
    public static Answer inherited(Store store, String scope, String nameFilter, boolean withDoc, Opts opts) {
        var wanted = scope == null ? null : scope.trim();
        var type = wanted == null ? null : store.allDecls()
                .filter(d -> d.kind().isType() && matchesType(d, wanted)).findFirst().orElse(null);
        /* Falling back silently would print a plain api header, leaving no sign that --inherited had
         * been dropped - and an empty answer would then read as "this type inherits nothing". */
        if (type == null) {
            var plain = api(store, scope, nameFilter, withDoc, opts);
            var why = wanted == null
                    ? "# --inherited needs a type; it was ignored because only a name filter was given.\n"
                    : "# --inherited was ignored: no type named '" + wanted + "' is indexed here, so there is "
                            + "no hierarchy to walk. The plain api answer follows.\n";
            return new Answer(plain.exit(), why + plain.text());
        }

        var chain = new LinkedHashSet<String>();
        collectSupers(store, type.key(), chain, 0);
        var seen = new HashSet<String>();
        var named = NameFilter.of(nameFilter);
        var sb = new StringBuilder(Fmt.meta("api " + type.key() + " --inherited"
                        + (nameFilter == null ? "" : " matching '" + nameFilter + "'"),
                type, store.root(), store.meta(), Fmt.plural(chain.size(), "type", "types") + " in the hierarchy"));
        var shown = 0;
        for (var owner : chain) {
            var members = store.declsInType(owner).stream()
                    .filter(opts::visible)
                    .filter(d -> !d.kind().isType())
                    .filter(d -> named.matches(d.name()))
                    /* An override re-declares an inherited member; show the one nearest the type asked
                     * about, which is the first reached walking outwards. */
                    .filter(d -> seen.add(d.name() + "(" + String.join(",", d.paramTypes()) + ")"))
                    .sorted(Comparator.comparing(Decl::name))
                    .toList();
            if (members.isEmpty()) continue;
            sb.append('\n').append(owner).append(owner.equals(type.key()) ? "   (declared here)" : "   (inherited)")
                    .append('\n');
            for (var d : members) {
                sb.append("  ").append(d.signature()).append('\n');
                if (withDoc && d.doc() != null) sb.append("      ").append(d.doc()).append('\n');
                shown++;
            }
            if (shown >= opts.limit()) break;
        }
        if (shown == 0) sb.append("# nothing callable found across the hierarchy")
                .append(nameFilter == null ? ".\n" : " matching '" + nameFilter + "'.\n");
        sb.append("# ").append(shown).append(" members across ").append(chain.size()).append(" types\n");
        return shown == 0 ? Answer.nothing(sb.toString()) : Answer.ok(sb.toString());
    }

    /** The type itself first, then outwards, so a nearer override wins over the one it overrides. */
    static void collectSupers(Store store, String key, Set<String> into, int depth) {
        if (depth > 24 || !into.add(key)) return;
        store.supersOf(key).forEach(s -> collectSupers(store, s, into, depth + 1));
    }

    public static Answer api(Store store, String scope, String nameFilter, boolean withDoc, Opts opts) {
        /* Not Spec.parse: a scope is a package or a type, never a member, and the symbol parser reads a
         * lower-case final segment as a member name — so "luvml.o" was becoming the scope "luvml". */
        var wanted = scope == null ? null : scope.trim();
        var isType = wanted != null && store.allDecls().anyMatch(d -> d.kind().isType() && matchesType(d, wanted));
        var named = NameFilter.of(nameFilter);
        var hits = store.allDecls()
                .filter(d -> isType ? ownedBy(d, wanted) : inScope(d, wanted))
                .filter(d -> isType || d.kind().isType() || nameFilter != null)
                .filter(d -> named.matches(d.name()))
                .filter(opts::visible)
                .sorted(Comparator.comparing(Decl::ownerKey, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Decl::name))
                .limit(opts.limit())
                .toList();

        var subject = "api" + (scope == null ? "" : " " + scope) + (nameFilter == null ? "" : " matching '" + nameFilter + "'");
        var sb = new StringBuilder(Fmt.meta(subject, null, store.root(), store.meta(),
                Fmt.plural(hits.size(), "declaration", "declarations")));
        if (hits.isEmpty()) return emptyApi(store, wanted, nameFilter, sb);
        var counts = hits.size() <= COUNTS_UP_TO && store.meta().hasReferences();
        if (!counts && store.meta().hasReferences()) sb.append("# use counts omitted above ")
                .append(COUNTS_UP_TO).append(" results; narrow with --name or a tighter scope to see them\n");
        var currentOwner = new Object() { String value = "\0"; };
        for (var d : hits) {
            var owner = d.ownerKey() == null ? packageOf(d.key()) : d.ownerKey();
            if (!owner.equals(currentOwner.value)) {
                currentOwner.value = owner;
                sb.append('\n').append(owner).append('\n');
            }
            sb.append("  ").append(d.signature());
            if (counts) sb.append("   ").append(store.refCount(d.key())).append(" refs");
            sb.append('\n');
            if (withDoc && d.doc() != null) sb.append("      ").append(d.doc()).append('\n');
            if (opts.withCode()) sb.append(codeOf(store, d));
        }
        return Answer.ok(sb.toString());
    }

    /**
     * An empty type and a nonexistent one are different answers. Saying "nothing matched" for a real,
     * public, empty interface asserts it does not exist — and for a marker interface the whole API is
     * inherited, so the useful reply names the supertypes to look at instead.
     */
    static Answer emptyApi(Store store, String wanted, String nameFilter, StringBuilder sb) {
        var type = wanted == null ? null : store.allDecls()
                .filter(d -> d.kind().isType() && matchesType(d, wanted)).findFirst().orElse(null);
        if (type == null) {
            sb.append("# nothing named '").append(wanted == null ? nameFilter : wanted)
                    .append("' exists here. jacli tree lists the packages and type names that do.\n");
            return Answer.nothing(sb.toString());
        }
        sb.append("# ").append(type.signature()).append(" exists and declares no ")
                .append(nameFilter == null ? "public members of its own" : "public member matching '" + nameFilter + "'")
                .append(".\n");
        var supers = store.supersOf(type.key());
        if (supers.isEmpty()) sb.append("# no supertypes are recorded for it, so it looks genuinely empty.\n");
        else {
            sb.append("# its API is inherited. Look at:\n");
            supers.forEach(s -> sb.append("#   jacli api ").append(s).append('\n'));
        }
        sb.append("# subtypes: jacli impls ").append(type.key()).append('\n');
        return Answer.nothing(sb.toString());
    }

    /**
     * The source of one declaration, indented under its signature. This is the whole-selection source
     * dump {@code symbols gen -c} produced, expressed as an option on {@code api} so the selection is
     * chosen by the same scope and name filter as the surface itself.
     */
    static String codeOf(Store store, Decl d) {
        if (d.implicit()) return "      (compiler-generated, no source)\n";
        if (d.file() == null || d.file().endsWith(".jar")) return "      (from bytecode; jacli src shows a decompiled body)\n";
        try {
            var lines = SourceView.read(Path.of(d.file()));
            var text = SourceView.slice(lines, d.line(), Math.max(d.line(), d.endLine()), false);
            return text.lines().map(l -> "      " + l).collect(Collectors.joining("\n", "", "\n"));
        } catch (RuntimeException e) {
            return "      (source unreadable: " + e.getMessage() + ")\n";
        }
    }

    static boolean matchesType(Decl d, String wanted) {
        return d.key().equals(wanted) || SymbolKey.simpleNameOf(d.key()).equals(wanted) || d.key().endsWith("." + wanted);
    }

    static boolean ownedBy(Decl d, String wanted) {
        return d.ownerKey() != null && matchesTypeKey(d.ownerKey(), wanted);
    }

    static boolean matchesTypeKey(String key, String wanted) {
        return key.equals(wanted) || SymbolKey.simpleNameOf(key).equals(wanted) || key.endsWith("." + wanted);
    }

    static boolean inScope(Decl d, String prefix) {
        if (prefix == null) return true;
        var key = d.key();
        return key.equals(prefix) || key.startsWith(prefix + ".")
                || (d.ownerKey() != null && d.ownerKey().startsWith(prefix));
    }

    static String packageOf(String typeKey) {
        var dot = typeKey.lastIndexOf('.');
        return dot < 0 ? "" : typeKey.substring(0, dot);
    }

    /** A nested type belongs to its owner's package, not to a package named after its owner. */
    static String packageOfType(Decl d) {
        return packageOf(d.ownerKey() == null ? d.key() : d.ownerKey());
    }

    /** {@code Outer.Inner} for a nested type, so it is visible and directly usable as a spec. */
    static String displayName(Decl d) {
        if (d.ownerKey() == null) return d.name();
        return SymbolKey.simpleNameOf(d.ownerKey()) + "." + d.name();
    }
}
