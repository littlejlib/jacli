package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.Tier;

/**
 * What the answer is worth. Carried on every response for the same reason gdrivecli reports index
 * coverage: a degraded or stale index that presents as a clean one turns "not resolved" into
 * "no usages", which is the one failure mode that would make this tool worse than grep.
 */
public record Meta(String project, Path root, Tier tier, int files, int declCount, int refCount,
                   int javacErrors, int unresolved, String classpathSource, boolean fresh,
                   String staleness, List<String> notes) {

    public String line() {
        return tier + "  " + files + " files, " + declCount + " declarations, " + refCount + " references"
                + (javacErrors > 0 ? ", " + javacErrors + " javac errors" : "")
                + (unresolved > 0 ? ", " + unresolved + " unresolved" : "")
                + "  classpath=" + classpathSource
                + (fresh ? "" : "  STALE: " + staleness);
    }

    /**
     * Whether use counts mean anything here. A target read straight from a dependency has no
     * reference index, and printing "0 refs" against every member of it would read as "all unused".
     */
    public boolean hasReferences() { return refCount > 0; }

    public List<String> warnings() {
        var out = new ArrayList<String>();
        if (!tier.isExact()) out.add(tier.caveat());
        if (!fresh) out.add("index is stale: " + staleness);
        out.addAll(notes);
        return out;
    }
}
