package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Builds;
import littlejlib.jacli.proj.Fingerprint;
import littlejlib.jacli.proj.Home;

/**
 * The gradle model, obtained the same way the maven classpath is: cache, then the build tool offline,
 * then the build tool online, then a static read of the build files. Every step is named in the
 * answer, so a degraded model can never be mistaken for gradle's own word.
 * <p>
 * Everything is cached against a fingerprint of the build files, including the static fallback.
 * Caching only the successes was measured on the maven side to cost six to eight seconds per query on
 * a project the build tool could not resolve — the expensive attempts have to happen once, not once
 * per question.
 */
public final class Gradle {

    private Gradle() { }

    public static final String CACHE = "gradle";

    public static GradleModel model(Path root, boolean includeTests, boolean force, Consumer<String> log) {
        var token = Home.token(root) + (includeTests ? "-tests" : "");
        var modelFile = Home.file(CACHE, token + ".model");
        var stampFile = Home.file(CACHE, token + ".stamp");
        var stamp = stampOf(root);

        var cachedSource = force ? null : cachedSource(stampFile, stamp);
        if (cachedSource != null) {
            var text = read(modelFile);
            if (text != null && !text.isBlank())
                return GradleModel.parse(root, text, "cache:" + cachedSource);
        }

        var model = run(root, includeTests, log);
        if (model != null) return store(modelFile, stampFile, stamp, withIncludedBuilds(model, root));
        return store(modelFile, stampFile, stamp, StaticModel.of(root, includeTests, log)
                .withNote("gradle itself could not be asked" + (GradleRun.usesWrapper(root) ? ""
                        : " (no gradlew in this project and no gradle on PATH would answer)")
                        + ", so the build was read from its files. Subprojects, source roots and the "
                        + "classpath are conventional rather than gradle's own answer, and the tier "
                        + "above says what that cost."));
    }

    /**
     * The cached model if there is a current one, and never a gradle run. For callers that must stay
     * cheap — {@code discover} would otherwise report a build gradle has already described as
     * {@code unknown:teavm:0}, because {@code gradle.properties} is the only thing a reader with no
     * gradle can consult and teavm keeps its version under a name of its own.
     */
    public static GradleModel cached(Path root) {
        var token = Home.token(root);
        if (cachedSource(Home.file(CACHE, token + ".stamp"), stampOf(root)) == null) return null;
        var text = read(Home.file(CACHE, token + ".model"));
        return text == null || text.isBlank() ? null : GradleModel.parse(root, text, "cache");
    }

    /**
     * Offline, then online — but the escalation is decided by how much the offline model RESOLVED, not
     * by gradle's exit code.
     * <p>
     * Per-project resolution failures are caught inside the init script, so that one subproject nobody
     * can resolve does not cost the whole model. The price of that is an exit code of 0 on a build where
     * sixteen of twenty-five projects came back with a lenient, hole-ridden classpath, which is what
     * teavm did against a half-warm cache. A ladder that reads only the exit code stops there and serves
     * it.
     */
    static GradleModel run(Path root, boolean includeTests, Consumer<String> log) {
        var offline = parse(root, GradleRun.model(root, true, includeTests, log), "gradle-offline", log);
        if (offline != null && offline.incomplete() == 0) return offline;
        if (!GradleRun.enabled()) return offline;
        log.accept(offline == null ? "offline gradle model failed, retrying online"
                : offline.incomplete() + " projects resolved incompletely offline, retrying online");
        var online = parse(root, GradleRun.model(root, false, includeTests, log), "gradle-online", log);
        if (online == null) return offline;
        if (offline == null || online.incomplete() <= offline.incomplete()) return online;
        log.accept("the online model resolved no better than the offline one; keeping the offline model");
        return offline;
    }

    static GradleModel parse(Path root, String text, String source, Consumer<String> log) {
        if (text == null) return null;
        var model = GradleModel.parse(root, text, source);
        if (!model.modules().isEmpty()) return model;
        log.accept(source + " configured but declared no java source set");
        return null;
    }

    /**
     * Included builds are separate builds, so invoking one task does not describe them — teavm's
     * {@code build-logic} is pulled in through {@code pluginManagement}, whose tasks are not even
     * addressable from the main build. Their sources are real java that the index should hold, so they
     * are added from their files, without a classpath of their own.
     */
    static GradleModel withIncludedBuilds(GradleModel model, Path root) {
        var known = model.modules().stream().map(m -> m.dir().toAbsolutePath().normalize())
                .collect(Collectors.toSet());
        var extra = new ArrayList<GradleModule>();
        for (var build : Settings.includedBuilds(root)) {
            var dirs = new LinkedHashMap<String, Path>();
            var name = build.getFileName().toString();
            dirs.put(name + ":", build);
            Settings.projects(build).forEach((p, d) -> dirs.put(name + p, d));
            dirs.forEach((path, dir) -> {
                if (known.contains(dir.toAbsolutePath().normalize())) return;
                var main = StaticModel.mainSources(dir);
                if (main.isEmpty()) return;
                extra.add(new GradleModule(path, dir, main, StaticModel.testSources(dir),
                        StaticModel.outputs(dir), List.of(), List.of()));
            });
        }
        if (extra.isEmpty()) return model;
        var all = new ArrayList<>(model.modules());
        all.addAll(extra);
        return new GradleModel(model.root(), model.coords(), List.copyOf(all), model.source(),
                append(model.notes(), extra.size() + " included-build project(s) were added from their files "
                        + "rather than from gradle - " + extra.stream().map(GradleModule::path).sorted()
                        .collect(Collectors.joining(", ")) + ". Their own dependencies are not on the "
                        + "classpath, so types they take from elsewhere may not resolve."));
    }

    static List<String> append(List<String> notes, String note) {
        var all = new ArrayList<>(notes);
        all.add(note);
        return List.copyOf(all);
    }

    static GradleModel store(Path modelFile, Path stampFile, String stamp, GradleModel model) {
        write(modelFile, model.render());
        write(stampFile, stamp + "\n" + model.source());
        return model;
    }

    /** Fingerprint of every file whose content could change the model. */
    public static String stampOf(Path root) {
        var dirs = StaticModel.discover(root, new ArrayList<>()).values().stream().distinct().toList();
        return Fingerprint.digest(Builds.buildFiles(root, dirs).stream()
                .map(p -> Fingerprint.stamp(root, p.toString(), p)).toList());
    }

    static String cachedSource(Path stampFile, String stamp) {
        var saved = read(stampFile);
        if (saved == null) return null;
        var lines = saved.strip().lines().toList();
        if (lines.isEmpty() || !lines.getFirst().trim().equals(stamp)) return null;
        return lines.size() > 1 ? lines.get(1).trim() : "gradle";
    }

    static String read(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void write(Path p, String s) {
        try {
            Files.writeString(p, s, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // a model cache that cannot be written only costs time on the next call
        }
    }
}
