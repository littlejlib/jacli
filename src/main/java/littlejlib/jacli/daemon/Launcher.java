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

    static final long MAX_LOG_BYTES = 8L * 1024 * 1024;
    static final int KEEP = 3;

    static void spawn(Consumer<String> log) throws IOException {
        var cmd = new ArrayList<String>();
        cmd.add(javaBinary());
        cmd.add("-cp");
        cmd.add(classpath());
        cmd.add(Server.class.getName());
        log.accept("starting daemon: " + String.join(" ", cmd));
        var logFile = Home.file(LOG);
        rotate(logFile);
        new ProcessBuilder(cmd)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                .redirectErrorStream(true)
                .start();
    }

    /**
     * The daemon's output is an OS-level append with no framework behind it, so nothing bounded it:
     * daemon.log reached 571 MB, larger than jacli's own index. Rolls at 8 MB keeping 3 generations.
     *
     * <p>Rotation happens here, at spawn, because that is the only moment this process controls. A
     * daemon that runs for months without a restart can still grow past the cap within that one run;
     * bounding it properly from inside would mean a logging framework rather than a redirect. In
     * practice the daemon restarts whenever the jar changes or the machine reboots, which is often
     * enough for this to hold.
     *
     * <p>A log that cannot be rotated must never stop the daemon starting, hence the swallowed
     * IOException.
     */
    static void rotate(Path logFile) {
        try {
            if (!Files.exists(logFile) || Files.size(logFile) < MAX_LOG_BYTES) return;
            Files.deleteIfExists(logFile.resolveSibling(LOG + "." + KEEP));
            for (var i = KEEP - 1; i >= 1; i--) {
                var from = logFile.resolveSibling(LOG + "." + i);
                if (Files.exists(from))
                    Files.move(from, logFile.resolveSibling(LOG + "." + (i + 1)), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(logFile, logFile.resolveSibling(LOG + ".1"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Not worth failing a daemon start over.
        }
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
