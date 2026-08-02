package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.proj.Cwd;

/**
 * The thin half. Forwards argv to the daemon, prints what comes back, exits with the same code.
 * <p>
 * If there is no daemon it starts one; if the daemon is running older code than this client, it is
 * told to stop and a fresh one is started. Failing all of that, the caller falls back to running
 * in-process — a query is worth answering slowly rather than not at all.
 */
public final class Client {

    private Client() { }

    public static final int CONNECT_TIMEOUT_MS = 3000, READ_TIMEOUT_MS = 600_000, START_WAIT_MS = 25_000;

    public static Response send(List<String> args, boolean autoStart, Consumer<String> log) throws IOException {
        var info = Info.read();
        if (info == null || !info.processAlive()) {
            if (!autoStart) throw new ConnectException("no daemon running");
            log.accept("no daemon running, starting one");
            info = Launcher.startAndWait(log);
        }
        var response = attempt(info, args);
        if (response.isStaleBuild()) {
            log.accept("daemon was running " + response.stamp() + ", this client is " + Build.STAMP
                    + " - restarting it");
            Launcher.awaitExit(info, log);
            info = Launcher.startAndWait(log);
            response = attempt(info, args);
        }
        return response;
    }

    static Response attempt(Info info, List<String> args) throws IOException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), info.port()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            Protocol.writeRequest(socket.getOutputStream(), Build.STAMP, Cwd.get().toString(), args);
            return Protocol.readResponse(socket.getInputStream());
        }
    }

    public static String ping() {
        try {
            var info = Info.read();
            if (info == null || !info.processAlive()) return null;
            return attempt(info, List.of("--daemon-ping")).stdout().trim();
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean stop(Consumer<String> log) {
        var info = Info.read();
        if (info == null) return false;
        try {
            attempt(info, List.of("--daemon-stop"));
            Launcher.awaitExit(info, log);
            return true;
        } catch (IOException e) {
            log.accept("daemon did not answer a stop request (" + e.getMessage() + "), killing pid " + info.pid());
            ProcessHandle.of(info.pid()).ifPresent(ProcessHandle::destroy);
            Info.clear();
            return true;
        }
    }
}
