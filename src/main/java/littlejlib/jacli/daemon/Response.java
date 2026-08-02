package littlejlib.jacli.daemon;

public record Response(int exit, String stamp, String stdout, String stderr) {

    public static final int STALE_BUILD = 97;

    public boolean isStaleBuild() { return exit == STALE_BUILD; }
}
