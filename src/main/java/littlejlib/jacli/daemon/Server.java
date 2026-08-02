package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.index.*;

/**
 * The resident half. Holds the ArcadeDB index open and a warm javac file manager per project, which
 * together are the difference between a 4 second warm call and a 100 millisecond one.
 * <p>
 * Requests are executed one at a time under a lock. Claude Code issues parallel tool calls, but
 * neither embedded ArcadeDB nor a JavacTask is thread safe, and a query costs milliseconds — so
 * queueing is both correct and cheap, where concurrency would be neither.
 */
public final class Server {

    public static final int IDLE_SHUTDOWN_MINUTES = 360, ACCEPT_BACKLOG = 32;

    final ProjectIndex index;
    final ServerSocket socket;
    final Object lock = new Object();
    final AtomicLong lastRequest = new AtomicLong(System.currentTimeMillis());
    final AtomicBoolean stopping = new AtomicBoolean();
    final Watcher watcher;

    final Hot hot;

    Server() throws IOException {
        socket = new ServerSocket(0, ACCEPT_BACKLOG, InetAddress.getLoopbackAddress());
        index = new ProjectIndex(Db.get().db());
        ServerContext.install(index);
        hot = new Hot(index::release);
        index.freshness(hot);
        watcher = new Watcher(index, hot);
    }

    public static void main(String[] args) throws IOException {
        var server = new Server();
        server.run();
    }

    void run() throws IOException {
        new Info(socket.getLocalPort(), ProcessHandle.current().pid(), Build.STAMP, System.currentTimeMillis()).write();
        Runtime.getRuntime().addShutdownHook(new Thread(this::cleanup, "jacli-shutdown"));
        watcher.start();
        idleWatchdog();
        System.err.println("jacli daemon listening on " + socket.getLocalPort() + ", build " + Build.STAMP);
        var pool = Executors.newCachedThreadPool(Server::daemonThread);
        while (!stopping.get()) {
            try {
                var client = socket.accept();
                pool.submit(() -> serve(client));
            } catch (IOException e) {
                if (!stopping.get()) System.err.println("jacli daemon accept failed: " + e.getMessage());
            }
        }
        pool.shutdownNow();
        cleanup();
        System.exit(0);
    }

    /**
     * Worker threads must be daemon threads. With non-daemon workers the JVM lingers after the accept
     * loop ends, so a client restarting a stale daemon waits out its kill timeout for no reason.
     */
    static Thread daemonThread(Runnable r) {
        var t = new Thread(r, "jacli-serve");
        t.setDaemon(true);
        return t;
    }

    void serve(Socket client) {
        lastRequest.set(System.currentTimeMillis());
        try (client; var in = client.getInputStream(); var out = client.getOutputStream()) {
            var request = Protocol.readRequest(in);
            if (!request.stampMatches(Build.STAMP)) {
                Protocol.writeResponse(out, Response.STALE_BUILD, Build.STAMP, "",
                        "daemon is running build " + Build.STAMP + " but the client is " + request.expectedStamp());
                stop();
                return;
            }
            if (request.args().size() == 1 && "--daemon-ping".equals(request.args().getFirst())) {
                Protocol.writeResponse(out, 0, Build.STAMP, "ok " + Build.STAMP + "\n", "");
                return;
            }
            if (request.args().size() == 1 && "--daemon-stop".equals(request.args().getFirst())) {
                Protocol.writeResponse(out, 0, Build.STAMP, "stopping\n", "");
                stop();
                return;
            }
            var result = execute(request);
            Protocol.writeResponse(out, result.exit(), Build.STAMP, result.stdout(), result.stderr());
        } catch (IOException e) {
            System.err.println("jacli daemon request failed: " + e);
        } finally {
            lastRequest.set(System.currentTimeMillis());
        }
    }

    Response execute(Request request) {
        synchronized (lock) {
            return Captured.run(request.cwd(), request.args());
        }
    }

    void idleWatchdog() {
        var t = new Thread(() -> {
            while (!stopping.get()) {
                try {
                    Thread.sleep(Duration.ofMinutes(5));
                } catch (InterruptedException e) {
                    return;
                }
                var idleMinutes = Duration.ofMillis(System.currentTimeMillis() - lastRequest.get()).toMinutes();
                if (idleMinutes >= IDLE_SHUTDOWN_MINUTES) {
                    System.err.println("jacli daemon idle for " + idleMinutes + " min, shutting down");
                    stop();
                }
            }
        }, "jacli-idle");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        if (!stopping.compareAndSet(false, true)) return;
        try {
            socket.close();
        } catch (IOException e) {
            // closing to break out of accept; failure here changes nothing
        }
    }

    final AtomicBoolean cleaned = new AtomicBoolean();

    /** Runs from the accept loop and from the shutdown hook, so it must be idempotent. */
    void cleanup() {
        if (!cleaned.compareAndSet(false, true)) return;
        stopping.set(true);
        watcher.stop();
        hot.closeAll();
        index.closeWarm();
        Db.shutdown();
        Info.clear();
    }
}
