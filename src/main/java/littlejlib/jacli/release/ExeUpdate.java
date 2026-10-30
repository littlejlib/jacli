package littlejlib.jacli.release;

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;

/** jr's own updater, run on one exe: {@code <exe> -Xjr:update-check|-Xjr:update -Xjr:batch}. */
final class ExeUpdate {

    static final int CURRENT = 0, ERROR = 1, NEWER = 10;

    /** The one line jr printed for the user on the last run. */
    static String message = "";

    private ExeUpdate() { }

    static int check(Path exe) {
        return run(exe, "-Xjr:update-check");
    }

    static int update(Path exe) {
        return run(exe, "-Xjr:update");
    }

    /** The exit code jr gave. */
    static int run(Path exe, String flag) {
        message = "";
        try {
            var p = new ProcessBuilder(exe.toString(), flag, "-Xjr:batch")
                    .redirectInput(ProcessBuilder.Redirect.from(new File("NUL")))
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var code = p.waitFor();
            message = lastLine(out);
            return code;
        } catch (IOException e) {
            message = "could not run " + exe + ": " + e.getMessage();
            return ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ERROR;
        }
    }

    static String lastLine(String stdout) {
        var found = "";
        for (var line : stdout.split("\n")) {
            if (!line.trim().startsWith("{")) continue;
            try {
                found = new ObjectMapper().readTree(line.trim()).path("message").asText(line.trim());
            } catch (IOException e) {
                found = line.trim();
            }
        }
        return found.isEmpty() ? stdout.trim() : found;
    }
}
