package littlejlib.jacli.proj;

import module java.base;

/**
 * What a verb was pointed at. jacli's index only covers local source trees, but the most valuable
 * moment for an API surface is a library you have never opened — so a target may also be maven
 * coordinates or a git URL, resolved to sources if they exist and read from bytecode if they do not.
 *
 * @param sourceDirs source roots to parse, empty for a bytecode-only target
 * @param jar the artifact to read class files from when there are no sources
 */
public record Target(Kind kind, String spec, Coords coords, Path root, List<Path> sourceDirs, Path jar,
                     String origin) {

    public enum Kind { LOCAL, MAVEN_SOURCES, MAVEN_CLASSES, GIT }

    public boolean hasSources() { return !sourceDirs.isEmpty(); }

    public boolean isLocal() { return kind == Kind.LOCAL; }

    public String describe() {
        return coords + "  [" + kind + "]  " + origin;
    }
}
