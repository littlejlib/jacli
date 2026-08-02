package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;
import com.sun.source.tree.IdentifierTree;

/**
 * Walks attributed trees once and collects every declaration and every resolved reference.
 * Measured at 66 ms for 13067 references over 488 files, so this deliberately records everything
 * rather than only what one verb needs.
 * <p>
 * Occurrences javac could not resolve are still recorded, under a {@link Keys#UNRESOLVED} key, so a
 * project that does not currently compile degrades to a structure-aware search instead of nothing.
 */
public final class Extractor extends TreePathScanner<Void, Void> {

    final Trees trees;
    final Types types;
    final Path root;
    final List<Decl> decls = new ArrayList<>();
    final List<Occurrence> refs = new ArrayList<>();
    final Map<String, SuperEdge> supers = new LinkedHashMap<>();
    final Deque<String> enclosing = new ArrayDeque<>();
    final Overrides overrides;
    final boolean recordUnresolved;
    String relFile = "";
    int unresolved = 0;

    /**
     * @param recordUnresolved keep occurrences javac could not bind. Only worth doing when javac
     *     reported errors: on a clean compile the unbindable nodes are package qualifiers and
     *     inferred {@code var} types, which are noise rather than missed references.
     */
    public Extractor(JavacTask task, Path root, boolean recordUnresolved) {
        this.trees = Trees.instance(task);
        this.types = task.getTypes();
        this.root = root;
        this.recordUnresolved = recordUnresolved;
        this.overrides = new Overrides(types, task.getElements());
    }

    public List<Decl> decls() { return decls; }

    public List<Occurrence> refs() { return refs; }

    public List<SuperEdge> supers() { return List.copyOf(supers.values()); }

    public int unresolved() { return unresolved; }

    public void scanUnit(CompilationUnitTree unit) {
        relFile = Src.rel(root, unit.getSourceFile());
        enclosing.clear();
        scan(unit, null);
    }

    @Override
    public Void visitClass(ClassTree t, Void v) {
        if (trees.getElement(getCurrentPath()) instanceof TypeElement te) {
            var key = SymbolKey.of(te, types);
            var direct = directSupertypeKeys(te);
            addDecl(te, key, direct);
            recordClosure(key, te, direct);
            addImplicitMembers(te, key);
            return inside(key, () -> super.visitClass(t, v));
        }
        return super.visitClass(t, v);
    }

    @Override
    public Void visitMethod(MethodTree t, Void v) {
        if (trees.getElement(getCurrentPath()) instanceof ExecutableElement me) {
            var key = SymbolKey.of(me, types);
            var overridden = overrides.overriddenKeys(me);
            addDecl(me, key, overridden);
            overridden.forEach(s -> edge(key, s, true));
            return inside(key, () -> super.visitMethod(t, v));
        }
        return super.visitMethod(t, v);
    }

    @Override
    public Void visitVariable(VariableTree t, Void v) {
        if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree
                && trees.getElement(getCurrentPath()) instanceof VariableElement fe)
            addDecl(fe, SymbolKey.of(fe, types), List.of());
        return super.visitVariable(t, v);
    }

    @Override public Void visitIdentifier(IdentifierTree t, Void v) { record(t.getName()); return super.visitIdentifier(t, v); }
    @Override public Void visitMemberSelect(MemberSelectTree t, Void v) { record(t.getIdentifier()); return super.visitMemberSelect(t, v); }
    @Override public Void visitNewClass(NewClassTree t, Void v) { record(null); return super.visitNewClass(t, v); }
    @Override public Void visitMemberReference(MemberReferenceTree t, Void v) { record(t.getName()); return super.visitMemberReference(t, v); }

    /** An element javac created to stand in for something it could not resolve. */
    static boolean isError(Element el) {
        if (el.getKind() == ElementKind.OTHER) return true;
        var type = el.asType();
        return type != null && type.getKind() == javax.lang.model.type.TypeKind.ERROR;
    }

    Void inside(String key, Supplier<Void> body) {
        enclosing.push(key);
        try {
            return body.get();
        } finally {
            enclosing.pop();
        }
    }

    /**
     * Members the compiler generates and no tree node represents: a record's accessors and canonical
     * constructor, an enum's {@code values}/{@code valueOf}, a default constructor.
     * <p>
     * Only source trees are visited, so these were absent entirely — and for a record the accessors ARE
     * the public API, so listing a record's members showed none of its read surface. Worse, a call to
     * one was indexed as a reference with no declaration to reach it by, which is a real use reported
     * as no use.
     */
    void addImplicitMembers(TypeElement te, String ownerKey) {
        var span = span(getCurrentPath());
        if (span == null) return;
        /* A record's accessor has no tree, but its component's field does — so point the accessor at the
         * component the caller is actually thinking of rather than at the record header. The position is
         * already in the index; using the header for all six accessors was throwing it away. */
        var componentSpans = new HashMap<String, int[]>();
        for (var member : te.getEnclosedElements()) {
            if (!(member instanceof VariableElement)) continue;
            var s = spanOf(trees.getTree(member));
            if (s != null) componentSpans.put(member.getSimpleName().toString(), s);
        }
        for (var member : te.getEnclosedElements()) {
            if (member instanceof TypeElement || trees.getTree(member) != null) continue;
            var key = SymbolKey.of(member, types);
            if (key == null) continue;
            var ret = member instanceof ExecutableElement m && m.getKind() != ElementKind.CONSTRUCTOR
                    ? SymbolKey.erased(m.getReturnType(), types)
                    : member instanceof VariableElement f ? SymbolKey.erased(f.asType(), types) : null;
            var ps = member instanceof ExecutableElement m
                    ? m.getParameters().stream().map(p -> SymbolKey.erased(p.asType(), types)).toList()
                    : List.<String>of();
            var supers = member instanceof ExecutableElement m ? overrides.overriddenKeys(m) : List.<String>of();
            supers.forEach(s -> edge(key, s, true));
            var at = componentSpans.getOrDefault(member.getSimpleName().toString(), span);
            decls.add(new Decl(key, DeclKind.of(member.getKind()), member.getSimpleName().toString(),
                    ownerKey, relFile, at[0], at[1], at[0], SymbolKey.signature(member, types),
                    SymbolKey.modifiers(member), ret, ps, supers,
                    "implicit: generated by the compiler, no source body", true));
        }
    }

    void addDecl(Element el, String key, List<String> superKeys) {
        if (key == null) return;
        var span = span(getCurrentPath());
        if (span == null) return;
        var ret = el instanceof ExecutableElement m && m.getKind() != ElementKind.CONSTRUCTOR
                ? SymbolKey.erased(m.getReturnType(), types)
                : el instanceof VariableElement f ? SymbolKey.erased(f.asType(), types) : null;
        var ps = el instanceof ExecutableElement m
                ? m.getParameters().stream().map(p -> SymbolKey.erased(p.asType(), types)).toList()
                : List.<String>of();
        decls.add(new Decl(key, DeclKind.of(el.getKind()), el.getSimpleName().toString(),
                el.getEnclosingElement() instanceof TypeElement o ? SymbolKey.of(o, types) : null,
                relFile, span[0], span[1], span[2],
                SymbolKey.signature(el, types), SymbolKey.modifiers(el), ret, ps,
                superKeys, Doc.firstSentence(trees.getDocComment(getCurrentPath()))));
    }

    void record(Name spelling) {
        var path = getCurrentPath();
        var el = trees.getElement(path);
        var key = (String) null;
        /* javac usually hands back an ERROR SYMBOL rather than null for something it could not bind, so
         * testing only for null meant the unresolved-marking almost never fired — and an answer on a
         * broken build then said the spelling was not referenced either, which is strictly false. */
        if (el == null || isError(el)) {
            if (spelling == null) return;
            unresolved++;
            if (!recordUnresolved) return;
            key = Keys.unresolved(spelling.toString());
        } else {
            key = SymbolKey.of(el, types);
            if (key == null) return;
        }
        var span = span(path);
        if (span == null) return;
        refs.add(new Occurrence(key, relFile, span[0], span[1], RefKinds.of(path, el), enclosing.peek()));
    }

    /**
     * Position of a tree reached from an Element rather than from a path. {@code Trees.getPath} returns
     * null for a record's component field, so asking for the path first lost the position and sent every
     * accessor to the record header.
     */
    int[] spanOf(Tree tree) {
        if (tree == null) return null;
        var unit = getCurrentPath().getCompilationUnit();
        var pos = trees.getSourcePositions();
        var start = pos.getStartPosition(unit, tree);
        if (start == Diagnostic.NOPOS) return null;
        var lm = unit.getLineMap();
        return new int[] { (int) lm.getLineNumber(start), (int) lm.getColumnNumber(start),
                (int) lm.getLineNumber(start) };
    }

    /** {@code {line, col, endLine}} or null when javac has no position for the tree. */
    int[] span(TreePath path) {
        var unit = path.getCompilationUnit();
        var pos = trees.getSourcePositions();
        var start = pos.getStartPosition(unit, path.getLeaf());
        if (start == Diagnostic.NOPOS) return null;
        var end = pos.getEndPosition(unit, path.getLeaf());
        var lm = unit.getLineMap();
        return new int[] { (int) lm.getLineNumber(start), (int) lm.getColumnNumber(start),
                end == Diagnostic.NOPOS ? (int) lm.getLineNumber(start) : (int) lm.getLineNumber(end) };
    }

    List<String> directSupertypeKeys(TypeElement te) {
        var out = new ArrayList<String>();
        for (var s : types.directSupertypes(te.asType())) {
            if (types.asElement(s) instanceof TypeElement t && !t.getQualifiedName().contentEquals("java.lang.Object"))
                out.add(SymbolKey.of(t, types));
        }
        return out;
    }

    /**
     * Records every supertype, not only the immediate ones. A subtype relation that passes through a
     * type declared in another project — luvml reaching luvx.Frag_I through luvx-base — is invisible
     * otherwise, and javac already has the whole chain from the classpath.
     */
    void recordClosure(String key, TypeElement te, List<String> direct) {
        if (key == null) return;
        direct.forEach(s -> edge(key, s, true));
        for (var t : overrides.supertypes(te)) {
            var sk = SymbolKey.of(t, types);
            if (sk != null && !direct.contains(sk)) edge(key, sk, false);
        }
    }

    void edge(String childKey, String superKey, boolean direct) {
        if (childKey == null || superKey == null) return;
        var e = new SuperEdge(childKey, superKey, direct);
        supers.merge(e.id(), e, (existing, fresh) -> existing.direct() ? existing : fresh);
    }
}
