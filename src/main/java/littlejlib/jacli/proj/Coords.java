package littlejlib.jacli.proj;

public record Coords(String groupId, String artifactId, String version, String type) {

    public static final Coords UNKNOWN = new Coords("unknown", "unknown", "0", null);
    public static final String POM = "pom";

    public Coords(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null);
    }

    public static Coords parse(String gav) {
        var p = gav.split(":");
        return p.length >= 4 ? new Coords(p[0], p[1], p[2], p[3])
                : p.length == 3 ? new Coords(p[0], p[1], p[2], null)
                : p.length == 2 ? new Coords(p[0], p[1], null, null) : UNKNOWN;
    }

    /** A pom-type dependency is an aggregator or a bill of materials; it never has a jar. */
    public boolean isPomType() { return POM.equals(type); }

    @Override
    public String toString() { return groupId + ":" + artifactId + ":" + version; }

    public String flat() { return (groupId + "__" + artifactId + "__" + version).replaceAll("[^A-Za-z0-9_.-]", "_"); }
}
