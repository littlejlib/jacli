package littlejlib.jacli.core;

import module java.base;

public record Analysis(Tier tier, List<Decl> decls, List<Occurrence> refs, List<SuperEdge> supers,
                       int fileCount, int errors, List<String> errorLines, int unresolved,
                       long parseMs, long analyzeMs, long extractMs) {

    public static Analysis empty(Tier tier) {
        return new Analysis(tier, List.of(), List.of(), List.of(), 0, 0, List.of(), 0, 0, 0, 0);
    }

    public long totalMs() { return parseMs + analyzeMs + extractMs; }

    public String timings() {
        return "parse=" + parseMs + "ms analyze=" + analyzeMs + "ms extract=" + extractMs + "ms";
    }

    public long resolvedRefs() { return refs.stream().filter(Occurrence::resolved).count(); }
}
