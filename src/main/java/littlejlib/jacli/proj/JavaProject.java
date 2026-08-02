package littlejlib.jacli.proj;

import module java.base;

/** A resolved project: where its sources are, what it is called, and what it compiles against. */
public record JavaProject(Path root, Coords coords, List<Path> sourceRoots, List<Path> moduleRoots,
                          Resolved classpath, List<Path> javaFiles) {

    public String id() { return Home.token(root); }

    public int fileCount() { return javaFiles.size(); }

    public String describe() {
        return coords + "  " + root + "  " + javaFiles.size() + " java files"
                + (moduleRoots.isEmpty() ? "" : ", " + moduleRoots.size() + " modules");
    }
}
