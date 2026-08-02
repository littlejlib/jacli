package littlejlib.jacli.daemon;

import module java.base;
import littlejlib.jacli.proj.Home;

/** Where the running daemon is, written once at startup and deleted on a clean stop. */
public record Info(int port, long pid, String stamp, long startedAt) {

    public static final String FILE = "daemon.txt";

    public static Path path() { return Home.file(FILE); }

    public void write() {
        try {
            Files.writeString(path(), port + "\n" + pid + "\n" + stamp + "\n" + startedAt + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Info read() {
        try {
            if (!Files.isRegularFile(path())) return null;
            var lines = Files.readAllLines(path(), StandardCharsets.UTF_8);
            if (lines.size() < 4) return null;
            return new Info(Integer.parseInt(lines.get(0).trim()), Long.parseLong(lines.get(1).trim()),
                    lines.get(2).trim(), Long.parseLong(lines.get(3).trim()));
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(path());
        } catch (IOException e) {
            // a leftover info file is handled by the client's connect failure path
        }
    }

    public boolean processAlive() {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    public String describe() {
        return "pid " + pid + " on port " + port + ", build " + stamp
                + ", up " + Duration.ofMillis(System.currentTimeMillis() - startedAt).toMinutes() + " min";
    }
}
