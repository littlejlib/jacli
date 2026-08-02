package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;
import com.sun.source.tree.IdentifierTree;

/**
 * Parse-only extraction, for when a project does not compile or has no resolvable classpath.
 * javac's parser recovers from errors and speaks current syntax, so this needs no second parser
 * library and no classpath at all.
 * <p>
 * Type declarations still get real keys — a type's qualified name follows from the package and the
 * nesting, with nothing to resolve. Everything else is marked {@link Keys#UNRESOLVED}, because a
 * parameter type here is a spelling and not an identity.
 */
public final class SyntacticExtractor extends TreePathScanner<Void, Void> {

    final Trees trees;
    final Path root;
    final List<Decl> decls = new ArrayList<>();
    final List<Occurrence> refs = new ArrayList<>();
    final Deque<String> enclosing = new ArrayDeque<>();
    final Deque<String> typeNesting = new ArrayDeque<>();
    String relFile = "", pkg = "";

    public SyntacticExtractor(JavacTask task, Path root) {
        this.trees = Trees.instance(task);
        this.root = root;
    }

    public List<Decl> decls() { return decls; }

    public List<Occurrence> refs() { return refs; }

    /** Only direct, spelled-out supertypes: without resolution there is no closure to walk. */
    public List<SuperEdge> supers() {
        return decls.stream().filter(d -> d.kind().isType())
                .flatMap(d -> d.superKeys().stream().map(s -> new SuperEdge(d.key(), s, true)))
                .distinct().toList();
    }

    public void scanUnit(CompilationUnitTree unit) {
        relFile = Src.rel(root, unit.getSourceFile());
        pkg = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        enclosing.clear();
        typeNesting.clear();
        scan(unit, null);
    }

    @Override
    public Void visitClass(ClassTree t, Void v) {
        if (t.getSimpleName().isEmpty()) return super.visitClass(t, v);
        typeNesting.push(t.getSimpleName().toString());
        var key = typeKey();
        add(key, DeclKind.of(kindOf(t)), t.getSimpleName().toString(), owner(), sig(t), supers(t));
        enclosing.push(key);
        try {
            return super.visitClass(t, v);
        } finally {
            enclosing.pop();
            typeNesting.pop();
        }
    }

    @Override
    public Void visitMethod(MethodTree t, Void v) {
        var key = Keys.unresolved(typeKey() + SymbolKey.MEMBER_SEP + t.getName());
        add(key, t.getReturnType() == null ? DeclKind.CONSTRUCTOR : DeclKind.METHOD,
                t.getName().toString(), typeKey(), sig(t), List.of());
        enclosing.push(key);
        try {
            return super.visitMethod(t, v);
        } finally {
            enclosing.pop();
        }
    }

    @Override
    public Void visitVariable(VariableTree t, Void v) {
        if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree)
            add(Keys.unresolved(typeKey() + SymbolKey.MEMBER_SEP + t.getName()), DeclKind.FIELD,
                    t.getName().toString(), typeKey(),
                    mods(t.getModifiers()) + t.getType() + " " + t.getName(), List.of());
        return super.visitVariable(t, v);
    }

    @Override public Void visitIdentifier(IdentifierTree t, Void v) { ref(t.getName().toString()); return super.visitIdentifier(t, v); }
    @Override public Void visitMemberSelect(MemberSelectTree t, Void v) { ref(t.getIdentifier().toString()); return super.visitMemberSelect(t, v); }
    @Override public Void visitMemberReference(MemberReferenceTree t, Void v) { ref(t.getName().toString()); return super.visitMemberReference(t, v); }

    @Override
    public Void visitNewClass(NewClassTree t, Void v) {
        var id = t.getIdentifier();
        ref(id instanceof IdentifierTree i ? i.getName().toString()
                : id instanceof MemberSelectTree m ? m.getIdentifier().toString() : null);
        return super.visitNewClass(t, v);
    }

    String typeKey() {
        var names = new ArrayList<>(typeNesting);
        Collections.reverse(names);
        var nested = String.join(".", names);
        return pkg.isEmpty() ? nested : pkg + "." + nested;
    }

    String owner() {
        if (typeNesting.size() < 2) return null;
        var names = new ArrayList<>(typeNesting);
        names.removeFirst();
        Collections.reverse(names);
        var nested = String.join(".", names);
        return pkg.isEmpty() ? nested : pkg + "." + nested;
    }

    void add(String key, DeclKind kind, String name, String ownerKey, String signature, List<String> supers) {
        add(key, kind, name, ownerKey, signature, supers, modifiersHere());
    }

    void add(String key, DeclKind kind, String name, String ownerKey, String signature, List<String> supers,
             String modifiers) {
        var span = span(getCurrentPath());
        if (span == null) return;
        decls.add(new Decl(key, kind, name, ownerKey, relFile, span[0], span[1], span[2], signature,
                modifiers, null, List.of(), supers, Doc.firstSentence(trees.getDocComment(getCurrentPath()))));
    }

    /**
     * Modifiers as written, plus the ones java implies. Without this every syntactic declaration looked
     * package-private, so anything filtering on public API — {@code api}, {@code unused} — silently
     * returned nothing for a target parsed without a classpath.
     */
    String modifiersHere() {
        var leaf = getCurrentPath().getLeaf();
        var written = leaf instanceof ClassTree c ? mods(c.getModifiers())
                : leaf instanceof MethodTree m ? mods(m.getModifiers())
                : leaf instanceof VariableTree v ? mods(v.getModifiers()) : "";
        var result = written.trim();
        return result.contains("public") || result.contains("protected") || result.contains("private")
                || !inInterface() ? result : ("public " + result).trim();
    }

    /** Members of an interface or annotation type are implicitly public. */
    boolean inInterface() {
        for (var p = getCurrentPath().getParentPath(); p != null; p = p.getParentPath())
            if (p.getLeaf() instanceof ClassTree c)
                return c.getKind() == Tree.Kind.INTERFACE || c.getKind() == Tree.Kind.ANNOTATION_TYPE;
        return false;
    }

    void ref(String spelling) {
        if (spelling == null || spelling.isEmpty()) return;
        var path = getCurrentPath();
        var span = span(path);
        if (span == null) return;
        refs.add(new Occurrence(Keys.unresolved(spelling), relFile, span[0], span[1],
                RefKinds.of(path, null), enclosing.peek()));
    }

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

    static ElementKind kindOf(ClassTree t) {
        return switch (t.getKind()) {
            case INTERFACE -> ElementKind.INTERFACE;
            case ENUM -> ElementKind.ENUM;
            case RECORD -> ElementKind.RECORD;
            case ANNOTATION_TYPE -> ElementKind.ANNOTATION_TYPE;
            default -> ElementKind.CLASS;
        };
    }

    static List<String> supers(ClassTree t) {
        var out = new ArrayList<String>();
        if (t.getExtendsClause() != null) out.add(Keys.unresolved(t.getExtendsClause().toString()));
        t.getImplementsClause().forEach(i -> out.add(Keys.unresolved(i.toString())));
        return out;
    }

    static String sig(ClassTree t) {
        return mods(t.getModifiers()) + SyntacticExtractor.word(t) + " " + t.getSimpleName()
                + (t.getExtendsClause() == null ? "" : " extends " + t.getExtendsClause())
                + (t.getImplementsClause().isEmpty() ? "" : " implements " + join(t.getImplementsClause()));
    }

    static String sig(MethodTree t) {
        var ps = new StringJoiner(", ");
        t.getParameters().forEach(p -> ps.add(p.getType() + " " + p.getName()));
        return mods(t.getModifiers()) + (t.getReturnType() == null ? "" : t.getReturnType() + " ")
                + t.getName() + "(" + ps + ")";
    }

    static String word(ClassTree t) {
        return switch (t.getKind()) {
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION_TYPE -> "@interface";
            default -> "class";
        };
    }

    static String join(List<? extends Tree> l) {
        var j = new StringJoiner(", ");
        l.forEach(x -> j.add(x.toString()));
        return j.toString();
    }

    static String mods(ModifiersTree m) {
        var j = new StringJoiner(" ");
        m.getFlags().forEach(f -> j.add(f.toString()));
        var s = j.toString();
        return s.isEmpty() ? "" : s + " ";
    }
}
