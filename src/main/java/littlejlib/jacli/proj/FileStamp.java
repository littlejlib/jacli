package littlejlib.jacli.proj;

/** Size and modification time only. The PRP is explicit: no content hashing, because it must be fast. */
public record FileStamp(String rel, long size, long mtime) {

    public boolean sameAs(FileStamp other) {
        return other != null && size == other.size && mtime == other.mtime;
    }

    public String line() { return rel + "|" + size + "|" + mtime; }
}
