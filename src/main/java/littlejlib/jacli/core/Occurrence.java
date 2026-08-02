package littlejlib.jacli.core;

/** One reference to {@code targetKey}, at a position, inside {@code enclosingKey}. */
public record Occurrence(String targetKey, String file, int line, int col, RefKind kind, String enclosingKey) {

    public String at() { return file + ":" + line + ":" + col; }

    public boolean resolved() { return !Keys.isUnresolved(targetKey); }
}
