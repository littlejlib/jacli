package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;
import javax.lang.model.element.Modifier;

/**
 * Which supertype methods a declaration overrides. Stored on the declaration so the reverse question
 * — who overrides this — is a single indexed lookup rather than a hierarchy walk at query time.
 */
public final class Overrides {

    final Types types;
    final Elements elements;
    final Map<String, List<TypeElement>> superCache = new HashMap<>();

    public Overrides(Types types, Elements elements) {
        this.types = types;
        this.elements = elements;
    }

    public List<String> overriddenKeys(ExecutableElement m) {
        if (m.getKind() != ElementKind.METHOD || m.getModifiers().contains(Modifier.STATIC)) return List.of();
        if (!(m.getEnclosingElement() instanceof TypeElement owner)) return List.of();
        var out = (List<String>) null;
        for (var sup : supertypes(owner)) {
            for (var cand : ElementFilter.methodsIn(sup.getEnclosedElements())) {
                if (!cand.getSimpleName().contentEquals(m.getSimpleName())) continue;
                if (cand.getParameters().size() != m.getParameters().size()) continue;
                if (!elements.overrides(m, cand, owner)) continue;
                if (out == null) out = new ArrayList<>();
                out.add(SymbolKey.of(cand, types));
            }
        }
        return out == null ? List.of() : out;
    }

    /** The transitive supertype closure, excluding the type itself and Object. Cached per type. */
    public List<TypeElement> supertypes(TypeElement t) {
        return superCache.computeIfAbsent(t.getQualifiedName().toString(), k -> {
            var acc = new LinkedHashMap<String, TypeElement>();
            collect(t.asType(), acc, 0);
            acc.remove(t.getQualifiedName().toString());
            return List.copyOf(acc.values());
        });
    }

    void collect(TypeMirror t, Map<String, TypeElement> acc, int depth) {
        if (depth > 24) return;
        for (var s : types.directSupertypes(t)) {
            if (!(types.asElement(s) instanceof TypeElement te)) continue;
            var name = te.getQualifiedName().toString();
            if (name.equals("java.lang.Object") || acc.putIfAbsent(name, te) != null) continue;
            collect(s, acc, depth + 1);
        }
    }
}
