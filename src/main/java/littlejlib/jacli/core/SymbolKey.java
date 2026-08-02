package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;
import javax.lang.model.element.Modifier;

/**
 * Stable textual identity for a java element. An {@code Element} is only comparable inside the
 * {@code JavacTask} that produced it, so every identity that crosses a task or reaches the index
 * goes through here. Index-time and query-time must call the same method or keys will not match.
 */
public final class SymbolKey {

    private SymbolKey() { }

    public static final String MEMBER_SEP = "#", CTOR = "<init>";

    public static String of(Element e, Types types) {
        if (e == null) return null;
        return switch (e) {
            case TypeElement t -> t.getQualifiedName().toString();
            case ExecutableElement m -> owner(m, types) + MEMBER_SEP + execName(m) + "(" + params(m, types) + ")";
            case VariableElement f when f.getEnclosingElement() instanceof TypeElement
                    -> owner(f, types) + MEMBER_SEP + f.getSimpleName();
            default -> null;
        };
    }

    static String owner(Element e, Types types) {
        var enc = e.getEnclosingElement();
        return enc instanceof TypeElement t ? t.getQualifiedName().toString() : String.valueOf(of(enc, types));
    }

    static String execName(ExecutableElement m) {
        return m.getKind() == ElementKind.CONSTRUCTOR ? CTOR : m.getSimpleName().toString();
    }

    public static String params(ExecutableElement m, Types types) {
        var j = new StringJoiner(",");
        m.getParameters().forEach(p -> j.add(erased(p.asType(), types)));
        return j.toString();
    }

    public static String erased(TypeMirror t, Types types) {
        var e = types == null ? t : types.erasure(t);
        var s = e.toString();
        var lt = s.indexOf('<');
        return lt < 0 ? s : s.substring(0, lt) + s.substring(s.lastIndexOf('>') + 1);
    }

    /** {@code luvml.o.Out_I#ba(byte[])} to {@code Out_I#ba(byte[])}. */
    public static String shortForm(String key) {
        if (key == null) return "";
        var hash = key.indexOf(MEMBER_SEP);
        var typePart = hash < 0 ? key : key.substring(0, hash);
        var dot = typePart.lastIndexOf('.');
        return (dot < 0 ? typePart : typePart.substring(dot + 1)) + (hash < 0 ? "" : key.substring(hash));
    }

    /** {@code luvml.o.Out_I#ba(byte[])} to {@code Out_I#ba} — parameters dropped, for column output. */
    public static String compactForm(String key) {
        var s = shortForm(key);
        var paren = s.indexOf('(');
        return paren < 0 ? s : s.substring(0, paren);
    }

    public static String typeOf(String key) {
        var hash = key.indexOf(MEMBER_SEP);
        return hash < 0 ? key : key.substring(0, hash);
    }

    public static String simpleNameOf(String key) {
        var hash = key.indexOf(MEMBER_SEP);
        if (hash >= 0) {
            var paren = key.indexOf('(', hash);
            return key.substring(hash + 1, paren < 0 ? key.length() : paren);
        }
        var dot = key.lastIndexOf('.');
        return dot < 0 ? key : key.substring(dot + 1);
    }

    /**
     * Every modifier the compiler reports, including the ones implicit in source. This is what gets
     * stored, because visibility has to be answerable from it: stripping the implied {@code public}
     * from interface members made every one of them look package-private, which silently emptied
     * {@code api} for the interface-heavy libraries it is most often pointed at.
     */
    public static String modifiers(Element e) {
        var j = new StringJoiner(" ");
        for (var m : Modifier.values()) if (e.getModifiers().contains(m)) j.add(m.toString());
        return j.toString();
    }

    /** For display only: an interface is not written "abstract", nor its members "public abstract". */
    public static String displayModifiers(Element e) {
        var implied = e.getKind() == ElementKind.INTERFACE || e.getKind() == ElementKind.ANNOTATION_TYPE
                ? Set.of(Modifier.ABSTRACT, Modifier.STATIC)
                : e.getEnclosingElement() != null && e.getEnclosingElement().getKind() == ElementKind.INTERFACE
                        ? Set.of(Modifier.PUBLIC, Modifier.ABSTRACT)
                        : Set.<Modifier>of();
        var j = new StringJoiner(" ");
        for (var m : Modifier.values())
            if (e.getModifiers().contains(m) && !implied.contains(m)) j.add(m.toString());
        return j.toString();
    }

    public static String signature(Element e, Types types) {
        return switch (e) {
            case ExecutableElement m -> {
                var mods = displayModifiers(m);
                var ret = m.getKind() == ElementKind.CONSTRUCTOR ? "" : erased(m.getReturnType(), types) + " ";
                var ps = new StringJoiner(", ");
                m.getParameters().forEach(p -> ps.add(erased(p.asType(), types) + " " + p.getSimpleName()));
                yield (mods.isEmpty() ? "" : mods + " ") + ret + execName(m) + "(" + ps + ")";
            }
            case VariableElement f -> {
                var mods = displayModifiers(f);
                yield (mods.isEmpty() ? "" : mods + " ") + erased(f.asType(), types) + " " + f.getSimpleName();
            }
            case TypeElement t -> {
                var mods = displayModifiers(t);
                yield (mods.isEmpty() ? "" : mods + " ") + kindWord(t) + " " + t.getQualifiedName();
            }
            default -> String.valueOf(e);
        };
    }

    static String kindWord(TypeElement t) {
        return switch (t.getKind()) {
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case RECORD -> "record";
            case ANNOTATION_TYPE -> "@interface";
            default -> "class";
        };
    }
}
