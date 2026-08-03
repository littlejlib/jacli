package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Home;
import littlejlib.jacli.proj.Proc;

/**
 * Asks gradle itself what the build looks like, through a read-only init script. The project's own
 * {@code gradlew} is preferred over any gradle on PATH, because a build declares the version it needs
 * and running it under another one fails in ways that look like jacli being broken.
 * <p>
 * No task of the build is executed — the model is written during configuration and {@code help} is
 * only there to give gradle something to do. On a composite build gradle still compiles the included
 * build's own logic in order to configure at all, which is the one place jacli's "never build unasked"
 * rule bends, and {@code status} says so.
 */
public final class GradleRun {

    private GradleRun() { }

    public static final int TIMEOUT_SECONDS = 900;
    public static final String INIT = "jacli-model.gradle", CACHE = "gradle";
    public static final String PROPERTY = "jacli.gradle.run", ENV = "JACLI_GRADLE_RUN";

    /**
     * Whether jacli may launch gradle at all. Off means the static reader answers everything, which is
     * what the tests want — they must stay offline and deterministic — and what anyone wants who would
     * rather jacli never started a build tool on their machine.
     */
    public static boolean enabled() {
        var prop = System.getProperty(PROPERTY);
        if (prop != null) return !isNo(prop);
        var env = System.getenv(ENV);
        return env == null || !isNo(env);
    }

    static boolean isNo(String s) {
        return "false".equalsIgnoreCase(s) || "0".equals(s) || "no".equalsIgnoreCase(s);
    }

    public static String launcher(Path root) {
        var wrapper = root.resolve(Proc.isWindows() ? "gradlew.bat" : "gradlew");
        return Files.isRegularFile(wrapper) ? wrapper.toAbsolutePath().toString() : "gradle";
    }

    public static boolean usesWrapper(Path root) { return !"gradle".equals(launcher(root)); }

    /** The init script, materialised next to the model cache so gradle can read it off disk. */
    public static Path initScript() {
        var file = Home.file(CACHE, INIT);
        var body = read();
        try {
            if (!Files.isRegularFile(file) || !Files.readString(file, StandardCharsets.UTF_8).equals(body))
                Files.writeString(file, body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    static String read() {
        try (var in = GradleRun.class.getResourceAsStream("/littlejlib/jacli/" + INIT)) {
            if (in == null) throw new IllegalStateException("jacli was built without " + INIT);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static final String TASK = "jacliModel";

    /**
     * Runs the dump and returns the concatenated chunks, or null when gradle would not answer.
     * <p>
     * A run that exits non-zero is discarded even when it wrote something. A failed configuration
     * writes whatever it managed before dying — on teavm the offline attempt produced a model
     * containing only the included build's 12 files, which parses perfectly and would have been served
     * as the answer for a 3,445-file project.
     */
    public static String model(Path root, boolean offline, boolean tests, Consumer<String> log) {
        if (!enabled()) {
            log.accept("launching gradle is switched off (" + PROPERTY + " / " + ENV
                    + "); reading the build files instead");
            return null;
        }
        /* Unconditional, not behind -v. Configuring a large build with a cold dependency cache runs for
         * minutes with nothing on the terminal, and the daemon serves one request at a time, so every
         * other jacli call waits behind it. A silent multi-minute pause reads as a hang. */
        System.err.println("jacli: asking gradle to describe " + root.getFileName() + " ("
                + (offline ? "offline" : "online") + "). First call only; minutes if its cache is cold.");
        var outBase = Home.file(CACHE, Home.token(root) + ".chunk");
        clearChunks(outBase);
        var cmd = new ArrayList<>(List.of(launcher(root), "-q", "--console=plain", "--no-configuration-cache",
                "-I", initScript().toAbsolutePath().toString(),
                "-Djacli.model.out=" + outBase.toAbsolutePath(),
                "-Djacli.model.tests=" + tests));
        if (offline) cmd.add("--offline");
        cmd.add(TASK);
        var ok = Proc.run(root, cmd, TIMEOUT_SECONDS, "gradle", log);
        var chunks = chunks(outBase);
        if (ok) return chunks.isEmpty() ? null : chunks;
        if (!chunks.isEmpty())
            log.accept("gradle failed but had written a partial model; discarding it, because a build that "
                    + "did not finish configuring describes only the projects it reached");
        return null;
    }

    static void clearChunks(Path outBase) {
        forEachChunk(outBase, p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                // a chunk left behind is re-read below only if gradle does not overwrite it
            }
        });
    }

    /** Root build first, so its {@code V}/{@code R} header leads and included builds append. */
    static String chunks(Path outBase) {
        var parts = new ArrayList<Path>();
        forEachChunk(outBase, parts::add);
        parts.sort(Comparator.comparing((Path p) -> p.getFileName().toString().endsWith(".root") ? 0 : 1)
                .thenComparing(p -> p.getFileName().toString()));
        var sb = new StringBuilder();
        for (var p : parts)
            try {
                sb.append(Files.readString(p, StandardCharsets.UTF_8));
            } catch (IOException e) {
                // an unreadable chunk simply contributes no modules
            }
        return sb.toString();
    }

    static void forEachChunk(Path outBase, Consumer<Path> action) {
        var dir = outBase.getParent();
        var prefix = outBase.getFileName().toString() + ".";
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var list = Files.list(dir)) {
            list.filter(p -> p.getFileName().toString().startsWith(prefix)).toList().forEach(action);
        } catch (IOException e) {
            // nothing to clear or read
        }
    }
}
