package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Builds;

/**
 * The subprojects a {@code settings.gradle(.kts)} declares. This is gradle's answer to maven's
 * {@code <modules>}, and following it rather than globbing the disk is what keeps a dead directory
 * out of the index — teavm carries {@code html4j}, {@code samples/gamepad} and {@code tools/eclipse}
 * with full source trees that no longer belong to the build, plus archetype templates under
 * {@code src/main/resources} that look exactly like source roots to a glob.
 * <p>
 * When the includes are computed rather than written out, this yields nothing and the caller falls
 * back to {@link #glob}, which is approximate and says so.
 */
public final class Settings {

    private Settings() { }

    public static final int GLOB_DEPTH = 4;

    static final Pattern OVERRIDE = Pattern.compile(
            "project\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)\\s*\\.\\s*projectDir\\s*=[^\"'\\n]*[\"']([^\"']+)[\"']");
    static final Pattern ROOT_NAME = Pattern.compile("rootProject\\s*\\.\\s*name\\s*=\\s*[\"']([^\"']+)[\"']");

    public static String source(Path root) {
        var settings = Builds.settings(root);
        return settings == null ? "" : Dsl.read(settings);
    }

    public static String rootName(Path root) {
        var m = ROOT_NAME.matcher(source(root));
        return m.find() ? m.group(1) : root.getFileName().toString();
    }

    /** Gradle project path to directory, root project excluded. */
    public static Map<String, Path> projects(Path root) {
        var src = source(root);
        var out = new LinkedHashMap<String, Path>();
        for (var i : Dsl.occurrences(src, "include"))
            for (var a : Dsl.args(src, i + "include".length())) out.put(path(a), dirOf(root, a));
        var parent = root.getParent();
        if (parent != null)
            for (var i : Dsl.occurrences(src, "includeFlat"))
                for (var a : Dsl.args(src, i + "includeFlat".length())) out.put(path(a), parent.resolve(a));
        var m = OVERRIDE.matcher(src);
        while (m.find()) out.put(path(m.group(1)), root.resolve(m.group(2)).normalize());
        out.values().removeIf(d -> !Files.isDirectory(d));
        return out;
    }

    /** Builds pulled in with {@code includeBuild}, which are separate builds sharing one workspace. */
    public static List<Path> includedBuilds(Path root) {
        var src = source(root);
        var out = new ArrayList<Path>();
        for (var i : Dsl.occurrences(src, "includeBuild"))
            for (var a : Dsl.args(src, i + "includeBuild".length())) {
                var dir = root.resolve(a).normalize();
                if (Files.isDirectory(dir) && !out.contains(dir)) out.add(dir);
            }
        var buildSrc = root.resolve("buildSrc");
        if (Files.isDirectory(buildSrc) && Builds.hasGradle(buildSrc) && !out.contains(buildSrc)) out.add(buildSrc);
        return out;
    }

    /** Directories carrying a build script, for a settings file whose includes are computed. */
    public static List<Path> glob(Path root) {
        var out = new ArrayList<Path>();
        walk(root, root, out, 0);
        return out;
    }

    static void walk(Path root, Path dir, List<Path> out, int depth) {
        if (depth > GLOB_DEPTH) return;
        try (var children = Files.list(dir)) {
            for (var child : children.filter(Files::isDirectory).sorted().toList()) {
                var name = child.getFileName().toString();
                if (name.startsWith(".") || name.equals("build") || name.equals("src")
                        || name.equals("gradle") || name.equals("node_modules")) continue;
                if (Builds.script(child) != null && !out.contains(child)) out.add(child);
                walk(root, child, out, depth + 1);
            }
        } catch (IOException e) {
            // an unreadable directory contributes no modules, which is the honest answer
        }
    }

    /** Does an ancestor's settings file claim this directory, the way a parent pom claims a module? */
    public static boolean claims(Path settingsRoot, Path dir) {
        var target = dir.toAbsolutePath().normalize();
        if (projects(settingsRoot).values().stream().anyMatch(p -> p.toAbsolutePath().normalize().equals(target)))
            return true;
        return includedBuilds(settingsRoot).stream().anyMatch(p -> p.toAbsolutePath().normalize().equals(target));
    }

    public static String path(String spec) {
        var s = spec.startsWith(":") ? spec.substring(1) : spec;
        return ":" + s;
    }

    public static Path dirOf(Path root, String spec) {
        var p = root;
        for (var seg : path(spec).substring(1).split(":")) if (!seg.isEmpty()) p = p.resolve(seg);
        return p.normalize();
    }
}
