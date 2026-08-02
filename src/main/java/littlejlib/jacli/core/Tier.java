package littlejlib.jacli.core;

/**
 * How much the engine could actually establish. Reported on every answer, because a degraded tier
 * silently presented as a clean one would let "not resolved" read as "no usages".
 */
public enum Tier {
    /** javac attributed everything, no errors. Overloads and inheritance are exact. */
    RESOLVED,
    /** javac attributed with errors. Exact where it resolved; the rest is reported as unresolved. */
    PARTIAL,
    /** Parse only — no classpath or attribution failed. Names are not resolved, overloads not distinguished. */
    SYNTACTIC,
    /** Even parsing failed. Word-boundary text matching only, which is what grep would have given. */
    TEXTUAL;

    public boolean isExact() { return this == RESOLVED; }

    public String caveat() {
        return switch (this) {
            case RESOLVED -> "";
            case PARTIAL -> "PARTIAL: some references could not be resolved; unresolved candidates are marked ~. Overloads and inheritance are exact only for resolved hits.";
            case SYNTACTIC -> "SYNTACTIC: parse only, nothing resolved. Same-named members of unrelated types are NOT distinguished and inheritance is not followed. Treat as a structure-aware grep.";
            case TEXTUAL -> "TEXTUAL: the file could not be parsed. Word-boundary matches only, no better than grep.";
        };
    }
}
