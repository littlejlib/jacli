package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.RefKind;

public record Opts(int limit, boolean overrides, int depth, boolean withDoc, boolean numbered,
                   Set<RefKind> kinds, boolean all, Member member, boolean nonPublic, boolean withCode) {

    /**
     * Narrows a spec to one kind of member. Needed because a field and a no-argument method can share
     * a name and a field has no parenthesised form, so the candidate list could only offer a spec
     * byte-identical to the input that had just failed.
     */
    public enum Member { ANY, FIELD, METHOD }

    public static Opts defaults() {
        return new Opts(Fmt.DEFAULT_LIMIT, false, 1, false, false, Set.of(), false, Member.ANY, false, false);
    }

    public boolean accepts(RefKind k) { return kinds.isEmpty() || kinds.contains(k); }

    public boolean acceptsDecl(littlejlib.jacli.core.Decl d) {
        return switch (member) {
            case ANY -> true;
            case FIELD -> d.kind() == littlejlib.jacli.core.DeclKind.FIELD
                    || d.kind() == littlejlib.jacli.core.DeclKind.ENUM_CONSTANT;
            case METHOD -> d.kind().isExecutable();
        };
    }

    /** For a key with no declaration behind it: only a method's key carries a parameter list. */
    public boolean acceptsKey(String key) {
        return switch (member) {
            case ANY -> true;
            case FIELD -> !key.contains("(");
            case METHOD -> key.contains("(");
        };
    }

    public Opts withNonPublic(boolean b) { return new Opts(limit, overrides, depth, withDoc, numbered, kinds, all, member, b, withCode); }

    public Opts withCode(boolean b) { return new Opts(limit, overrides, depth, withDoc, numbered, kinds, all, member, nonPublic, b); }

    /** Public API only, unless asked otherwise. */
    public boolean visible(littlejlib.jacli.core.Decl d) { return nonPublic || d.isPublicApi(); }

    public Opts withMember(Member m) {
        return new Opts(limit, overrides, depth, withDoc, numbered, kinds, all, m, nonPublic, withCode);
    }

    public Opts withKinds(Set<RefKind> k) { return new Opts(limit, overrides, depth, withDoc, numbered, k, all, member, nonPublic, withCode); }

    public Opts withLimit(int l) { return new Opts(l, overrides, depth, withDoc, numbered, kinds, all, member, nonPublic, withCode); }

    public Opts withDepth(int d) { return new Opts(limit, overrides, d, withDoc, numbered, kinds, all, member, nonPublic, withCode); }

    public Opts withOverrides(boolean b) { return new Opts(limit, b, depth, withDoc, numbered, kinds, all, member, nonPublic, withCode); }

    public Opts withAll(boolean b) { return new Opts(limit, overrides, depth, withDoc, numbered, kinds, b, member, nonPublic, withCode); }

    public Opts withDoc(boolean b) { return new Opts(limit, overrides, depth, b, numbered, kinds, all, member, nonPublic, withCode); }

    public Opts withNumbered(boolean b) { return new Opts(limit, overrides, depth, withDoc, b, kinds, all, member, nonPublic, withCode); }
}
