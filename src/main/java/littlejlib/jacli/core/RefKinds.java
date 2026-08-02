package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;

/** Classifies a reference occurrence from its surrounding tree, so {@code callers} can ask for invocations only. */
public final class RefKinds {

    private RefKinds() { }

    public static RefKind of(TreePath path, Element el) {
        var leaf = path.getLeaf();
        if (leaf instanceof NewClassTree) return RefKind.NEW;
        if (leaf instanceof MemberReferenceTree) return RefKind.METHOD_REF;

        var parent = path.getParentPath() == null ? null : path.getParentPath().getLeaf();
        if (parent instanceof MethodInvocationTree mi && mi.getMethodSelect() == leaf) return RefKind.CALL;
        if (parent instanceof ImportTree) return RefKind.IMPORT;
        if (parent instanceof AnnotationTree a && a.getAnnotationType() == leaf) return RefKind.ANNOTATION;

        if (el instanceof VariableElement) return RefKind.FIELD;
        if (el instanceof ExecutableElement) return RefKind.CALL;

        if (parent instanceof ClassTree c) {
            if (c.getExtendsClause() == leaf) return RefKind.EXTENDS;
            if (c.getImplementsClause().contains(leaf)) return RefKind.IMPLEMENTS;
            if (c.getPermitsClause().contains(leaf)) return RefKind.PERMITS;
        }
        if (parent instanceof MethodTree m && m.getThrows().contains(leaf)) return RefKind.THROWS;
        return RefKind.TYPE;
    }
}
