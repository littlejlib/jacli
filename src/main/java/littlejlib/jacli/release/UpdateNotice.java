package littlejlib.jacli.release;

import module java.base;
import jarrunner.jr.apputils.UpdateResult;
import jarrunner.jr.apputils.Updater;
import littlejlib.jacli.proj.Home;

/** One check a day (jr's own cache), and the one-line notice on stderr at most once a day, so an agent running jacli a hundred times is told once. */
public final class UpdateNotice {

    static final Duration DAY = Duration.ofDays(1);
    static volatile String notice;
    static Thread check;

    private UpdateNotice() { }

    public static void start() {
        check = new Thread(() -> {
            try {
                var r = Updater.checkAtMostEvery(DAY);
                if (r.status() == UpdateResult.Status.NEWER) notice = Updater.notice(r, "update");
            } catch (RuntimeException e) {
                // a check must never break a command
            }
        }, "update-check");
        check.setDaemon(true);
        check.start();
    }

    /** Waits briefly for the day's first check, then prints the notice if one is due. */
    public static void show() {
        if (check == null) return;
        try {
            check.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        var n = notice;
        if (n == null || !due()) return;
        System.err.println(n);
        try {
            Files.writeString(marker(), Instant.now().toString());
        } catch (IOException e) {
            // no marker only means the next command says it again
        }
    }

    static Path marker() {
        return Home.file("update-notified");
    }

    static boolean due() {
        try {
            return Files.getLastModifiedTime(marker()).toInstant().plus(DAY).isBefore(Instant.now());
        } catch (IOException e) {
            return true;
        }
    }
}
