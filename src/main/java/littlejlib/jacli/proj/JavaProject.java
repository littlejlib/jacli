package littlejlib.jacli.proj;

import module java.base;

/** A resolved project: which build declares it, where its sources are, and what it compiles against. */
public record JavaProject(BuildKind build, Path root, Coords coords, List<Path> sourceRoots,
                          List<Path> moduleRoots, Resolved classpath, List<Path> javaFiles) {

    public String id() { return Home.token(root); }

    public int fileCount() { return javaFiles.size(); }

    public String describe() {
        return coords + "  " + root + "  " + build.label() + ", " + javaFiles.size() + " java files"
                + (moduleRoots.isEmpty() ? "" : ", " + moduleRoots.size() + " modules");
    }
}
