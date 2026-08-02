package littlejlib.jacli.proj;

/**
 * One artifact in the resolved closure, at the shallowest depth it was reached. There are no children:
 * nearest-wins means each artifact appears exactly once, so depth is enough to render the shape.
 */
public record DepNode(Coords coords, int depth, Origin origin) {

    public String key() { return coords.groupId() + ":" + coords.artifactId(); }
}
