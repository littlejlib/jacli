package littlejlib.jacli.proj.gradle;

import littlejlib.jacli.proj.Coords;

/** One resolved dependency and how far from the project it sits in the resolution graph. */
public record GradleDep(int depth, Coords coords) {

    public String key() { return coords.groupId() + ":" + coords.artifactId(); }
}
