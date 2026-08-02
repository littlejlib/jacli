package littlejlib.jacli.proj;

import module java.base;
import org.eclipse.jgit.api.Git;

/**
 * Resolves a target the way the PRP specifies, best first: a local source tree, then maven
 * coordinates with a sources jar, then the same coordinates read from the binary jar's class files,
 * then a git clone. Only when none of those can be had does it fail — and then it says which step
 * ran out, because "no sources anywhere" and "not in your local repository" need different fixes.
 * <p>
 * Bytecode is read rather than decompiled. The compiler resolves signatures from class files
 * directly, so a decompiler in the middle would only add a dependency and a chance to be wrong.
 */
public final class Targets {

    private Targets() { }

    public static final String SRC_CACHE = "src", GIT_CACHE = "git";

    public static Target resolve(String spec, Consumer<String> log) {
        if (spec == null || spec.isBlank()) throw new IllegalArgumentException("empty target");
        if (looksLikeGit(spec)) return git(spec, log);
        if (looksLikeCoords(spec)) return maven(Coords.parse(spec), log);
        var dir = Path.of(spec);
        if (!Files.isDirectory(dir)) throw new IllegalStateException("not a directory, not maven "
                + "coordinates (groupId:artifactId:version) and not a git URL: " + spec);
        return local(dir);
    }

    public static Target local(Path anyPathInside) {
        var root = Projects.findRoot(anyPathInside);
        var modules = Projects.moduleRoots(root);
        return new Target(Target.Kind.LOCAL, root.toString(), Poms.coords(root.resolve(Poms.POM)), root,
                Projects.sourceRoots(root, modules, false), null, root.toString());
    }

    static boolean looksLikeCoords(String s) {
        return s.chars().filter(c -> c == ':').count() >= 2 && !s.contains("/") && !s.contains("\\");
    }

    static boolean looksLikeGit(String s) {
        return s.startsWith("http://") || s.startsWith("https://") || s.startsWith("git@") || s.endsWith(".git");
    }

    static Target maven(Coords c, Consumer<String> log) {
        var jar = M2.jarPath(c);
        if (jar == null) throw new IllegalStateException("incomplete coordinates: " + c
                + " - give groupId:artifactId:version");
        var sourcesJar = jar.getParent().resolve(jar.getFileName().toString().replace(".jar", "-sources.jar"));
        if (Files.isRegularFile(sourcesJar)) {
            var dir = Home.dir(SRC_CACHE, c.flat());
            unzipIfStale(sourcesJar, dir, log);
            return new Target(Target.Kind.MAVEN_SOURCES, c.toString(), c, dir, List.of(dir), jar,
                    sourcesJar.toString());
        }
        if (Files.isRegularFile(jar)) {
            log.accept("no sources jar; reading the API surface from " + jar.getFileName()
                    + "'s class files (signatures are exact, source positions are not available)");
            return new Target(Target.Kind.MAVEN_CLASSES, c.toString(), c, jar.getParent(), List.of(), jar,
                    jar.toString());
        }
        throw new IllegalStateException(c + " is not in your local repository. Fetch it with:\n"
                + "  mvn dependency:get -Dartifact=" + c + ":jar:sources     (best - real sources)\n"
                + "  mvn dependency:get -Dartifact=" + c + "                 (jar only - still gives signatures)");
    }

    static Target git(String url, Consumer<String> log) {
        var dir = Home.dir(GIT_CACHE, repoToken(url));
        if (Files.isDirectory(dir.resolve(".git"))) log.accept("reusing clone at " + dir);
        else clone(url, dir, log);
        var root = Projects.findRoot(dir);
        var modules = Projects.moduleRoots(root);
        var sourceRoots = Projects.sourceRoots(root, modules, false);
        return new Target(Target.Kind.GIT, url, Poms.coords(root.resolve(Poms.POM)), root, sourceRoots, null, url);
    }

    static void clone(String url, Path dir, Consumer<String> log) {
        log.accept("shallow-cloning " + url + " into " + dir);
        try (var git = Git.cloneRepository().setURI(url).setDirectory(dir.toFile()).setDepth(1).call()) {
            log.accept("cloned " + git.getRepository().getBranch());
        } catch (Exception e) {
            var auth = String.valueOf(e.getMessage()).contains("Authentication is required");
            throw new IllegalStateException("could not clone " + url + ": " + e.getMessage()
                    + (auth ? "\njacli clones anonymously and holds no credentials. For a private repository, "
                            + "clone it yourself with your usual git and then point at the directory:\n"
                            + "  git clone " + url + " <dir>  &&  jacli tree -T <dir>" : ""), e);
        }
    }

    static String repoToken(String url) {
        var clean = url.replaceAll("\\.git$", "").replaceAll("^.*[/:]([^/]+/[^/]+)$", "$1");
        return clean.replaceAll("[^A-Za-z0-9_.-]", "__");
    }

    /** Extraction is skipped when the marker matches the jar's size and mtime. */
    static void unzipIfStale(Path zip, Path dir, Consumer<String> log) {
        var marker = dir.resolve(".jacli-source-of");
        var stamp = Fingerprint.stamp(zip.getParent(), zip.getFileName().toString(), zip).line();
        try {
            if (Files.isRegularFile(marker) && Files.readString(marker).trim().equals(stamp)) return;
            log.accept("extracting " + zip.getFileName() + " to " + dir);
            unzip(zip, dir);
            Files.writeString(marker, stamp, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void unzip(Path zip, Path dir) throws IOException {
        try (var zf = new java.util.zip.ZipFile(zip.toFile())) {
            for (var it = zf.entries(); it.hasMoreElements(); ) {
                var entry = it.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".java")) continue;
                var out = dir.resolve(entry.getName()).normalize();
                if (!out.startsWith(dir)) continue;
                Files.createDirectories(out.getParent());
                try (var in = zf.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
