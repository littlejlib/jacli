package littlejlib.jacli.proj;

import module java.base;

/** Runs a build tool as a child process, keeping a bounded tail of its output for diagnostics. */
public final class Proc {

    private Proc() { }

    public static final int MAX_CAPTURE = 8000, TAIL_LINES = 4;

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** A command line the platform shell will accept, so a {@code .cmd} or {@code .bat} launcher runs. */
    public static List<String> shell(List<String> cmd) {
        if (!isWindows()) return cmd;
        var out = new ArrayList<String>(List.of("cmd", "/c"));
        out.addAll(cmd);
        return out;
    }

    public static boolean run(Path dir, List<String> cmd, int timeoutSeconds, String what, Consumer<String> log) {
        try {
            var p = new ProcessBuilder(shell(cmd)).directory(dir.toFile()).redirectErrorStream(true).start();
            var out = new StringBuilder();
            try (var r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (var line = r.readLine(); line != null; line = r.readLine())
                    if (out.length() < MAX_CAPTURE) out.append(line).append('\n');
            }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.accept(what + " timed out after " + timeoutSeconds + "s in " + dir);
                return false;
            }
            if (p.exitValue() != 0) log.accept(what + " exit " + p.exitValue() + ": " + tail(out.toString()));
            return p.exitValue() == 0;
        } catch (IOException e) {
            log.accept(what + " could not be started (" + e.getMessage() + ")");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static String tail(String s) {
        var lines = s.strip().lines().toList();
        return String.join(" / ", lines.subList(Math.max(0, lines.size() - TAIL_LINES), lines.size()));
    }
}
