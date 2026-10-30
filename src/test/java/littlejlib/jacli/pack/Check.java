package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.query.*;

/// Asks two stores every question the verbs can ask and counts the answers that differ.
final class Check {

    final Store ref, got;
    int bad;

    Check(Store ref, Store got) { this.ref = ref; this.got = got; }

    static String d(Decl x) {
        return x.key() + "|" + x.file() + "|" + x.line() + "|" + x.col() + "|" + x.kind() + "|" + x.name() + "|"
                + x.ownerKey() + "|" + x.signature() + "|" + x.modifiers() + "|" + x.returnType() + "|"
                + x.paramTypes() + "|" + x.doc() + "|" + x.implicit() + "|" + x.endLine();
    }

    static String o(Occurrence x) {
        return x.targetKey() + "|" + x.file() + "|" + x.line() + "|" + x.col() + "|" + x.kind() + "|" + x.enclosingKey();
    }

    static Set<String> decls(Collection<Decl> l) {
        return l.stream().map(Check::d).collect(Collectors.toCollection(TreeSet::new));
    }

    static Set<String> keys(Collection<Decl> l) {
        return l.stream().map(Decl::key).collect(Collectors.toCollection(TreeSet::new));
    }

    static Set<String> occ(Collection<Occurrence> l) {
        return l.stream().map(Check::o).collect(Collectors.toCollection(TreeSet::new));
    }

    void same(String what, Object a, Object b) {
        if (!a.equals(b) && bad++ < 8)
            System.out.println("   DIFF " + what + "\n     ref " + trim(a) + "\n     got " + trim(b));
    }

    static String trim(Object x) {
        var s = String.valueOf(x);
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    int all(Analysis a) {
        var names = a.decls().stream().map(Decl::name).collect(Collectors.toCollection(TreeSet::new));
        for (var n : names) same("declsByName " + n, decls(ref.declsByName(n)), decls(got.declsByName(n)));
        same("declsByName(absent)", ref.declsByName("zzzNope"), got.declsByName("zzzNope"));
        var keys = a.decls().stream().map(Decl::key).collect(Collectors.toCollection(TreeSet::new));
        for (var k : keys) {
            same("declByKey " + k, ref.declByKey(k).map(Check::d).orElse("-"), got.declByKey(k).map(Check::d).orElse("-"));
            same("declsInType " + k, decls(ref.declsInType(k)), decls(got.declsInType(k)));
            same("supersOf " + k, new TreeSet<>(ref.supersOf(k)), new TreeSet<>(got.supersOf(k)));
            same("declsWithSuper " + k, keys(ref.declsWithSuper(k)), keys(got.declsWithSuper(k)));
            same("directSubtypesOf " + k, keys(ref.directSubtypesOf(k)), keys(got.directSubtypesOf(k)));
        }
        var targets = a.refs().stream().map(Occurrence::targetKey).collect(Collectors.toCollection(TreeSet::new));
        for (var t : targets) {
            same("refsTo " + t, occ(ref.refsTo(t)), occ(got.refsTo(t)));
            same("refCount " + t, ref.refsTo(t).size(), got.refCount(t));
        }
        var encl = a.refs().stream().map(Occurrence::enclosingKey).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        for (var e : encl) same("refsFrom " + e, occ(ref.refsFrom(e)), occ(got.refsFrom(e)));
        var simple = targets.stream().map(t -> SymbolKey.simpleNameOf(Keys.spelling(t)))
                .collect(Collectors.toCollection(TreeSet::new));
        for (var s : simple)
            same("targetKeysByName " + s, new TreeSet<>(ref.targetKeysByName(s)), new TreeSet<>(got.targetKeysByName(s)));
        same("referencedKeys", new TreeSet<>(ref.referencedKeys()), new TreeSet<>(got.referencedKeys()));
        same("allDecls count", ref.allDecls().count(), got.allDecls().count());
        same("allDecls keys", keys(ref.allDecls().toList()), keys(got.allDecls().toList()));
        return bad;
    }
}
