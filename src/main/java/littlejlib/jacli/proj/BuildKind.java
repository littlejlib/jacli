package littlejlib.jacli.proj;

/** Which build system describes a project root. PLAIN is a bare source tree with neither. */
public enum BuildKind {

    MAVEN, GRADLE, PLAIN;

    public String label() {
        return switch (this) {
            case MAVEN -> "maven";
            case GRADLE -> "gradle";
            case PLAIN -> "plain";
        };
    }
}
