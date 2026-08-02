package littlejlib.jacli.core;

import module java.base;

/**
 * A symbol as an agent writes it. An LSP is handed a cursor; a CLI is handed a name, so this is the
 * part with no upstream prior art and it has to be forgiving about form:
 * <pre>
 *   Out_I                      simple type name
 *   luvml.o.Out_I              qualified type
 *   Out_I#ba                   member, any overload
 *   Out_I#ba(byte[])           one overload
 *   luvml.o.HtmlRenderer.renderElement    dotted, as it appears in a stack trace
 *   ba                         bare member name, any owner
 * </pre>
 * The dotted form is ambiguous in principle, so the last segment starting with an upper-case letter
 * decides it is a type. Writing {@code #} removes the guess.
 */
public record Spec(String raw, String typeSpec, String memberName, String params) {

    public static Spec parse(String raw) {
        var s = raw.trim();
        var hash = s.indexOf(SymbolKey.MEMBER_SEP);
        if (hash >= 0) return member(s, s.substring(0, hash), s.substring(hash + 1));

        var dot = s.lastIndexOf('.');
        if (dot > 0) {
            var last = s.substring(dot + 1);
            var looksLikeType = last.isEmpty() || Character.isUpperCase(last.charAt(0)) || last.contains("(");
            if (!looksLikeType) return member(s, s.substring(0, dot), last);
        }
        return new Spec(s, s, null, null);
    }

    static Spec member(String raw, String typeSpec, String rest) {
        var paren = rest.indexOf('(');
        if (paren < 0) return new Spec(raw, typeSpec.isEmpty() ? null : typeSpec, rest, null);
        var close = rest.lastIndexOf(')');
        return new Spec(raw, typeSpec.isEmpty() ? null : typeSpec, rest.substring(0, paren),
                rest.substring(paren + 1, close < 0 ? rest.length() : close).trim());
    }

    public boolean isMember() { return memberName != null; }

    /** The identifier to shortlist and index-lookup on. */
    public String searchName() {
        return memberName != null ? memberName : SymbolKey.simpleNameOf(typeSpec);
    }

    public boolean matches(Decl d) {
        if (d.key() == null) return false;
        if (!isMember()) return d.kind().isType() && typeMatches(d.key());
        if (d.kind().isType()) return false;
        /* Parentheses in the spec mean "this is a method". A field can never match one, and letting it
         * do so put the exact input back in the candidate list as a remedy for itself — the same closed
         * loop as --overrides once had, and it fired on EVERY record, since every component generates a
         * field and an accessor of the same name. */
        if (params != null && !d.kind().isExecutable()) return false;
        if (!nameMatches(d)) return false;
        if (typeSpec != null && !typeMatches(d.ownerKey())) return false;
        return params == null || paramsMatch(d.paramTypes());
    }

    boolean nameMatches(Decl d) {
        if (d.name().equals(memberName)) return true;
        return d.kind() == DeclKind.CONSTRUCTOR && d.ownerKey() != null
                && SymbolKey.simpleNameOf(Keys.spelling(d.ownerKey())).equals(memberName);
    }

    /**
     * Matches a bare key with no declaration behind it — a symbol from a dependency rather than
     * from this project. Without this, {@code refs Collectors#joining} would report nothing simply
     * because the declaration lives in a jar.
     */
    public boolean matchesKey(String key) {
        if (key == null) return false;
        var k = Keys.spelling(key);
        var hash = k.indexOf(SymbolKey.MEMBER_SEP);
        if (!isMember()) return hash < 0 && typeMatches(k);
        if (hash < 0) return false;
        var paren = k.indexOf('(', hash);
        var name = k.substring(hash + 1, paren < 0 ? k.length() : paren);
        if (!name.equals(memberName)) return false;
        if (typeSpec != null && !typeMatches(k.substring(0, hash))) return false;
        /* Same rule for a key with no declaration behind it: only a method's key carries parentheses. */
        if (params != null && paren < 0) return false;
        if (params == null) return true;
        var close = k.lastIndexOf(')');
        var actual = k.substring(paren + 1, close < 0 ? k.length() : close);
        return paramsMatch(Arrays.stream(actual.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList());
    }

    boolean typeMatches(String key) {
        if (key == null) return false;
        var k = Keys.spelling(key);
        return k.equals(typeSpec) || SymbolKey.simpleNameOf(k).equals(typeSpec) || k.endsWith("." + typeSpec);
    }

    boolean paramsMatch(List<String> actual) {
        if (params.isEmpty()) return actual.isEmpty();
        var want = Arrays.stream(params.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
        if (want.size() != actual.size()) return false;
        for (var i = 0; i < want.size(); i++) if (!oneParamMatches(want.get(i), actual.get(i))) return false;
        return true;
    }

    static boolean oneParamMatches(String want, String actual) {
        return actual.equals(want) || lastSegment(actual).equals(lastSegment(want));
    }

    static String lastSegment(String t) {
        var arr = "";
        var base = t;
        while (base.endsWith("[]")) {
            arr += "[]";
            base = base.substring(0, base.length() - 2);
        }
        var dot = base.lastIndexOf('.');
        return (dot < 0 ? base : base.substring(dot + 1)) + arr;
    }
}
