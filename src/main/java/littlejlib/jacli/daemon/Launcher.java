package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.proj.Home;

/**
 * Starts the daemon as a detached child running the same code as this process — resolved from this
 * class's own code source, so a jar run and a target/classes run each start their own kind and a
 * rebuilt jar can never be started by an old launcher.
 */
public final class Launcher {

    private Launcher() { }

    public static final String LOG = "daemon.log";

    public static Info startAndWait(Consumer<String> log) throws IOException {
        spawn(log);
        var deadline = System.currentTimeMillis() + Client.START_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            var info = Info.read();
            if (info != null && info.processAlive() && Client.ping() != null) return info;
            sleep(60);
        }
        throw new ConnectException("daemon did not come up within " + Client.START_WAIT_MS
                + "ms; see " + Home.file(LOG));
    }

    static void spawn(Consumer<String> log) throws IOException {
        var cmd = new ArrayList<String>();
        cmd.add(javaBinary());
        cmd.add("-cp");
        cmd.add(classpath());
        cmd.add(Server.class.getName());
        log.accept("starting daemon: " + String.join(" ", cmd));
        new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(Home.file(LOG).toFile()))
                .redirectErrorStream(true)
                .start();
    }

    static String javaBinary() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name).toString();
    }

    /**
     * The running classpath. {@code java.class.path} is empty when launched from a jar with -jar, so
     * fall back to this class's own code source.
     */
    static String classpath() {
        var cp = System.getProperty("java.class.path", "");
        if (!cp.isBlank()) return cp;
        try {
            var source = Launcher.class.getProtectionDomain().getCodeSource();
            if (source != null) return Path.of(source.getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            // fall through to the failure below
        }
        throw new IllegalStateException("cannot determine jacli's own classpath to start a daemon");
    }

    public static void awaitExit(Info info, Consumer<String> log) {
        var handle = ProcessHandle.of(info.pid());
        if (handle.isEmpty()) {
            Info.clear();
            return;
        }
        var deadline = System.currentTimeMillis() + 10_000;
        while (handle.get().isAlive() && System.currentTimeMillis() < deadline) sleep(50);
        if (handle.get().isAlive()) {
            log.accept("daemon pid " + info.pid() + " did not exit, killing it");
            handle.get().destroyForcibly();
        }
        Info.clear();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
