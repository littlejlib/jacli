package littlejlib.jacli.proj;

import module java.base;

/** Runs maven as a child process. {@code mvn} on this machine is the AOT launcher, a drop-in for stock mvn. */
public final class Mvn {

    private Mvn() { }

    public static final int TIMEOUT_SECONDS = 240;

    public static boolean buildClasspath(Path projectDir, Path outFile, boolean offline, Consumer<String> log) {
        var args = new ArrayList<String>();
        args.add("-q");
        if (offline) args.add("-o");
        args.add("dependency:build-classpath");
        args.add("-Dmdep.outputFile=" + outFile.toAbsolutePath());
        args.add("-Dmdep.includeScope=compile");
        args.add("-Dmdep.pathSeparator=" + File.pathSeparator);
        var ok = run(projectDir, args, log) && Files.isRegularFile(outFile);
        if (!ok) return false;
        try {
            return !Files.readString(outFile).isBlank();
        } catch (IOException e) {
            return false;
        }
    }

    static boolean run(Path dir, List<String> mvnArgs, Consumer<String> log) {
        var cmd = new ArrayList<String>();
        if (isWindows()) {
            cmd.add("cmd");
            cmd.add("/c");
        }
        cmd.add("mvn");
        cmd.addAll(mvnArgs);
        try {
            var p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
            var out = new StringBuilder();
            try (var r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (var line = r.readLine(); line != null; line = r.readLine())
                    if (out.length() < 8000) out.append(line).append('\n');
            }
            if (!p.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.accept("mvn timed out after " + TIMEOUT_SECONDS + "s in " + dir);
                return false;
            }
            if (p.exitValue() != 0) log.accept("mvn exit " + p.exitValue() + ": " + tail(out.toString()));
            return p.exitValue() == 0;
        } catch (IOException e) {
            log.accept("mvn could not be started (" + e.getMessage() + ")");
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static String tail(String s) {
        var lines = s.strip().lines().toList();
        return String.join(" / ", lines.subList(Math.max(0, lines.size() - 4), lines.size()));
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
