package littlejlib.jacli.proj.gradle;

import module java.base;

/** One gradle subproject as the model sees it: where its java is, where its classes land, what it compiles against. */
public record GradleModule(String path, Path dir, List<Path> mainSrc, List<Path> testSrc,
                           List<Path> output, List<String> classpath, List<GradleDep> deps) {

    public static GradleModule empty(String path, Path dir) {
        return new GradleModule(path, dir, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public List<Path> sources(boolean includeTests) {
        var out = new ArrayList<>(mainSrc);
        if (includeTests) out.addAll(testSrc);
        return out;
    }

    public boolean isRoot() { return ":".equals(path); }
}
