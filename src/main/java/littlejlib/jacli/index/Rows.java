package littlejlib.jacli.index;

import module java.base;
import littlejlib.jacli.core.*;

/** Index rows to engine records. The one place the storage shape and the query shape meet. */
public final class Rows {

    private Rows() { }

    /**
     * Rows store paths relative to their own project root, so a store spanning several projects has to
     * absolutise on the way out — otherwise a path from a sibling project would be resolved against the
     * wrong root. Everything downstream already tolerates this: {@code Src.abs} returns an absolute
     * path unchanged, so nothing else needed to know.
     */
    public static Decl toDecl(JDecl r, Path root) {
        return new Decl(r.key(), kind(r.kind()), r.name(), r.ownerKey(), abs(root, r.file()),
                n(r.line()), n(r.col()), n(r.endLine()), r.signature(),
                r.modifiers() == null ? "" : r.modifiers(), r.returnType(), params(r.params()),
                List.of(), r.doc(), Boolean.TRUE.equals(r.implicit()));
    }

    static String abs(Path root, String rel) {
        return rel == null ? null : littlejlib.jacli.core.Src.abs(root, rel).toString();
    }

    /** Supertypes are a separate row type, so a declaration read alone carries an empty list. */
    public static Decl withSupers(Decl d, List<String> superKeys) {
        return new Decl(d.key(), d.kind(), d.name(), d.ownerKey(), d.file(), d.line(), d.col(), d.endLine(),
                d.signature(), d.modifiers(), d.returnType(), d.paramTypes(), superKeys, d.doc(), d.implicit());
    }

    public static Occurrence toOccurrence(JRef r, Path root) {
        return new Occurrence(r.targetKey(), abs(root, r.file()), n(r.line()), n(r.col()),
                refKind(r.kind()), r.enclosingKey());
    }

    static List<String> params(String joined) {
        return joined == null || joined.isEmpty() ? List.of()
                : Arrays.stream(joined.split(",")).filter(s -> !s.isEmpty()).toList();
    }

    static int n(Integer i) { return i == null ? 0 : i; }

    static DeclKind kind(String s) {
        try {
            return s == null ? DeclKind.OTHER : DeclKind.valueOf(s);
        } catch (IllegalArgumentException e) {
            return DeclKind.OTHER;
        }
    }

    static RefKind refKind(String s) {
        try {
            return s == null ? RefKind.TYPE : RefKind.valueOf(s);
        } catch (IllegalArgumentException e) {
            return RefKind.TYPE;
        }
    }
}
