package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.core.Src;
import littlejlib.jacli.proj.Builds;
import littlejlib.jacli.proj.Coords;

/**
 * The model assembled by reading the build files, for when gradle cannot be run — no gradle on PATH,
 * an empty cache with no network, or a build that will not configure. It is the counterpart of the
 * maven side's walk of the local repository, and it is approximate in the same way and says so.
 * <p>
 * Convention is assumed where the scripts are silent: {@code src/main/java} for sources and
 * {@code build/classes/java/main} for output, plus any {@code srcDir} literal the script does spell.
 */
public final class StaticModel {

    private StaticModel() { }

    public static final String SOURCE = "gradle-static";
    public static final int DEP_DEPTH = 3;

    public static GradleModel of(Path root, boolean includeTests, Consumer<String> log) {
        var notes = new ArrayList<String>();
        var dirs = discover(root, notes);
        var catalog = Catalog.of(root);
        if (catalog.isEmpty() && Files.isRegularFile(root.resolve(Catalog.DEFAULT)))
            notes.add("gradle/libs.versions.toml is present but no library could be read from it");
        var buildFiles = Builds.buildFiles(root, dirs.values().stream().distinct().toList());
        var closure = Artifacts.closure(Deps.mentioned(buildFiles, catalog), DEP_DEPTH);
        var declared = Artifacts.closure(Deps.declared(buildFiles, catalog, includeTests), DEP_DEPTH);
        var jars = Artifacts.jars(closure);
        var unresolved = closure.stream().filter(d -> Artifacts.jar(d.coords()) == null).count();
        if (unresolved > 0) notes.add(unresolved + " of " + closure.size() + " dependencies are in neither the "
                + "gradle cache nor the maven repository, so types from them cannot resolve"
                + (GradleCache.available() ? "" : " (there is no gradle artifact cache on this machine at all)"));

        var modules = new ArrayList<GradleModule>();
        dirs.forEach((path, dir) -> modules.add(new GradleModule(path, dir, mainSources(dir), testSources(dir),
                outputs(dir), ":".equals(path) ? jars : List.of(),
                ":".equals(path) ? declared : List.of())));
        log.accept("gradle model read from build files: " + modules.size() + " projects, "
                + jars.size() + " resolvable jars");
        return new GradleModel(root, coords(root), List.copyOf(modules), SOURCE, List.copyOf(notes));
    }

    /** {@code gradle.properties} is the only place a script-free reader can find a group and version. */
    public static Coords coords(Path root) {
        var props = new Properties();
        var file = root.resolve("gradle.properties");
        if (Files.isRegularFile(file))
            try (var in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                // an unreadable gradle.properties just leaves the coordinates unknown
            }
        return new Coords(props.getProperty("group", "unknown"), Settings.rootName(root),
                props.getProperty("version", "0"));
    }

    /** Gradle project path to directory, root project first, included builds appended. */
    public static Map<String, Path> discover(Path root, List<String> notes) {
        var dirs = new LinkedHashMap<String, Path>();
        dirs.put(":", root);
        var declared = Settings.projects(root);
        if (!declared.isEmpty()) dirs.putAll(declared);
        else if (Builds.settings(root) != null) {
            var globbed = Settings.glob(root);
            if (!globbed.isEmpty()) notes.add("the settings file declares no literal include(...), so "
                    + globbed.size() + " subprojects were found by looking for build scripts on disk - a "
                    + "directory that is no longer part of the build can appear here");
            globbed.forEach(d -> dirs.put(pathOf(root, d), d));
        }
        for (var build : Settings.includedBuilds(root)) {
            var name = build.getFileName().toString();
            dirs.put(name + ":", build);
            Settings.projects(build).forEach((p, d) -> dirs.put(name + p, d));
        }
        return dirs;
    }

    public static String pathOf(Path root, Path dir) {
        return ":" + Src.rel(root, dir).replace('/', ':');
    }

    public static List<Path> mainSources(Path dir) { return sources(dir, "main"); }

    public static List<Path> testSources(Path dir) { return sources(dir, "test"); }

    static List<Path> sources(Path dir, String set) {
        var out = new ArrayList<Path>();
        addIfDir(out, dir.resolve("src").resolve(set).resolve("java"));
        var script = Builds.script(dir);
        if (script != null) {
            var src = Dsl.read(script);
            for (var keyword : List.of("srcDir", "srcDirs"))
                for (var at : Dsl.occurrences(src, keyword))
                    for (var a : Dsl.args(src, at + keyword.length()))
                        if (a.contains(set) || !a.contains("test")) addIfDir(out, dir.resolve(a).normalize());
        }
        return List.copyOf(out);
    }

    public static List<Path> outputs(Path dir) {
        var out = new ArrayList<Path>();
        var classes = dir.resolve("build").resolve("classes");
        for (var lang : List.of("java", "kotlin", "groovy", "scala")) addIfDir(out, classes.resolve(lang).resolve("main"));
        return List.copyOf(out);
    }

    static void addIfDir(List<Path> out, Path p) {
        if (Files.isDirectory(p) && !out.contains(p)) out.add(p);
    }
}
