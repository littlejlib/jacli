package littlejlib.jacli.proj;

import module java.base;
import littlejlib.jacli.core.Src;

/**
 * Finds the project a path belongs to and everything the engine needs about it. Multi-module
 * reactors are followed through {@code <modules>}, so one index covers a whole reactor and a
 * cross-module find-usages works without registering each module separately.
 */
public final class Projects {

    private Projects() { }

    public static final int MAX_MODULE_DEPTH = 6;

    public static Path findRoot(Path start) {
        var dir = start.toAbsolutePath().normalize();
        if (Files.isRegularFile(dir)) dir = dir.getParent();
        for (var d = dir; d != null; d = d.getParent()) {
            if (Files.isRegularFile(d.resolve(Poms.POM))) return highestReactorRoot(d);
            if (Files.isDirectory(d.resolve("src").resolve("main").resolve("java"))) return d;
        }
        return dir;
    }

    /** Walk up while the parent directory's pom lists this directory as a module. */
    static Path highestReactorRoot(Path dir) {
        var current = dir;
        for (var i = 0; i < MAX_MODULE_DEPTH; i++) {
            var parent = current.getParent();
            if (parent == null) return current;
            var parentPom = parent.resolve(Poms.POM);
            if (!Files.isRegularFile(parentPom)) return current;
            var name = current.getFileName().toString();
            if (!Poms.modules(parentPom).contains(name)) return current;
            current = parent;
        }
        return current;
    }

    public static List<Path> moduleRoots(Path root) {
        var out = new ArrayList<Path>();
        collectModules(root, out, 0);
        return out;
    }

    static void collectModules(Path dir, List<Path> out, int depth) {
        if (depth > MAX_MODULE_DEPTH) return;
        for (var m : Poms.modules(dir.resolve(Poms.POM))) {
            var sub = dir.resolve(m).normalize();
            if (!Files.isDirectory(sub) || out.contains(sub)) continue;
            out.add(sub);
            collectModules(sub, out, depth + 1);
        }
    }

    public static List<Path> sourceRoots(Path root, List<Path> moduleRoots, boolean includeTests) {
        var dirs = new ArrayList<Path>();
        var bases = new ArrayList<Path>();
        bases.add(root);
        bases.addAll(moduleRoots);
        for (var b : bases) {
            add(dirs, b.resolve("src").resolve("main").resolve("java"));
            if (includeTests) add(dirs, b.resolve("src").resolve("test").resolve("java"));
        }
        if (dirs.isEmpty()) add(dirs, root);
        return dirs;
    }

    static void add(List<Path> dirs, Path p) {
        if (Files.isDirectory(p)) dirs.add(p);
    }

    public static JavaProject open(Path anyPathInside, boolean includeTests, boolean forceClasspath, Consumer<String> log) {
        var root = findRoot(anyPathInside);
        var modules = moduleRoots(root);
        var sourceRoots = sourceRoots(root, modules, includeTests);
        var files = Src.javaFiles(sourceRoots);
        var cp = Classpath.resolve(root, modules, forceClasspath, log);
        return new JavaProject(root, Poms.coords(root.resolve(Poms.POM)), sourceRoots, modules, cp, files);
    }
}
