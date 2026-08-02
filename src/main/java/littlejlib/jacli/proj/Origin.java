package littlejlib.jacli.proj;

import module java.base;

/**
 * Where an artifact in the local repository came from, read offline from maven resolver's
 * {@code _remote.repositories} marker: {@code foo.jar>central=} means it was downloaded, while
 * {@code foo.jar>=} with an empty repository id means somebody ran {@code mvn install} locally.
 * <p>
 * That distinction is the whole releasability question. A build that works on this machine because a
 * sibling project was installed by hand will not work for anyone else.
 */
public record Origin(Kind kind, String repoId) {

    public enum Kind { REMOTE, LOCAL_ONLY, MISSING }

    public static final Origin MISSING = new Origin(Kind.MISSING, null);
    public static final String CENTRAL = "central";

    public static Origin of(Coords c) {
        var artifact = artifactPath(c);
        if (artifact == null || !Files.isRegularFile(artifact)) return MISSING;
        var marker = artifact.getParent().resolve("_remote.repositories");
        if (!Files.isRegularFile(marker)) return new Origin(Kind.LOCAL_ONLY, null);
        var prefix = artifact.getFileName() + ">";
        try {
            for (var line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
                var t = line.trim();
                if (!t.startsWith(prefix)) continue;
                var repo = t.substring(prefix.length()).replace("=", "").trim();
                return repo.isEmpty() ? new Origin(Kind.LOCAL_ONLY, null) : new Origin(Kind.REMOTE, repo);
            }
        } catch (IOException e) {
            return new Origin(Kind.LOCAL_ONLY, null);
        }
        return new Origin(Kind.LOCAL_ONLY, null);
    }

    /** The file that proves presence: a jar normally, but a pom-type dependency only ever has a pom. */
    public static Path artifactPath(Coords c) {
        var jar = M2.jarPath(c);
        if (jar == null) return null;
        return c.isPomType() ? jar.getParent().resolve(c.artifactId() + "-" + c.version() + ".pom") : jar;
    }

    /** Only an artifact actually published to central is available to anyone else. */
    public boolean isPublic() { return kind == Kind.REMOTE && CENTRAL.equals(repoId); }

    public boolean isMissing() { return kind == Kind.MISSING; }

    public String label() {
        return switch (kind) {
            case REMOTE -> CENTRAL.equals(repoId) ? "central" : "remote:" + repoId;
            case LOCAL_ONLY -> "LOCAL-ONLY";
            case MISSING -> "MISSING";
        };
    }
}
