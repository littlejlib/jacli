package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.FileStamp;
import static littlejlib.jacli.pack.Layout.*;

/// One project's index, read in place. Nothing is built at load: the bytes are the tables, and every
/// lookup is a binary search over them, so a pack costs what its file costs and no more.
public final class Pack {

    final byte[] raw;
    final ByteBuffer bb;
    final int[] off = new int[SECTIONS], len = new int[SECTIONS];
    final int stringBase;

    Pack(byte[] raw) {
        this.raw = raw;
        this.bb = ByteBuffer.wrap(raw);
        if (raw.length < HEADER * 4 || bb.getInt(H_MAGIC * 4) != MAGIC || bb.getInt(H_VERSION * 4) != VERSION)
            throw new IllegalArgumentException("not a pack file of this version");
        var end = 0;
        for (var s = 0; s < SECTIONS; s++) {
            off[s] = bb.getInt((H_SECTIONS + 2 * s) * 4);
            len[s] = bb.getInt((H_SECTIONS + 2 * s + 1) * 4);
            end = Math.max(end, off[s] + len[s]);
        }
        stringBase = end * 4;
    }

    public static Pack of(byte[] raw) { return new Pack(raw); }

    int head(int i) { return bb.getInt(i * 4); }

    int at(int section, int i) { return bb.getInt((off[section] + i) * 4); }

    int count(int section, int stride) { return len[section] / stride; }

    public int declCount() { return count(DECLS, DECL); }

    public int refCount() { return count(REFS, REF); }

    String str(int id) {
        if (id < 0) return null;
        var from = at(STR_OFFSETS, id);
        return new String(raw, stringBase + from, at(STR_OFFSETS, id + 1) - from, StandardCharsets.UTF_8);
    }

    /** The pool rank of a spelling, or -1 when the pack never mentions it. */
    int find(String s) {
        var b = s.getBytes(StandardCharsets.UTF_8);
        int lo = 0, hi = len[STR_OFFSETS] - 2;
        while (lo <= hi) {
            var mid = (lo + hi) >>> 1;
            var from = stringBase + at(STR_OFFSETS, mid);
            var c = Arrays.compareUnsigned(raw, from, stringBase + at(STR_OFFSETS, mid + 1), b, 0, b.length);
            if (c == 0) return mid;
            if (c < 0) lo = mid + 1; else hi = mid - 1;
        }
        return -1;
    }

    /** First and one-past-last index in [0, n) whose value equals {@code target}, values being non-decreasing. */
    static int[] range(int n, IntUnaryOperator value, int target) {
        int lo = 0, hi = n;
        while (lo < hi) {
            var mid = (lo + hi) >>> 1;
            if (value.applyAsInt(mid) < target) lo = mid + 1; else hi = mid;
        }
        var from = lo;
        hi = n;
        while (lo < hi) {
            var mid = (lo + hi) >>> 1;
            if (value.applyAsInt(mid) <= target) lo = mid + 1; else hi = mid;
        }
        return new int[] {from, lo};
    }

    /** Rows of a table whose field equals a spelling, through the permutation that orders that field. */
    List<Integer> rowsWhere(int perm, int table, int stride, int field, String spelling) {
        var id = find(spelling);
        if (id < 0) return List.of();
        var r = range(len[perm], i -> at(table, at(perm, i) * stride + field), id);
        var out = new ArrayList<Integer>(r[1] - r[0]);
        for (var i = r[0]; i < r[1]; i++) out.add(at(perm, i));
        return out;
    }

    /** The reference rows of one target are contiguous, so this is the range and nothing more. */
    int[] refRange(String targetKey) {
        var id = find(targetKey);
        if (id < 0) return new int[] {0, 0};
        var pairs = len[REF_TARGETS] / 2;
        var i = range(pairs, p -> at(REF_TARGETS, 2 * p), id);
        if (i[0] == i[1]) return new int[] {0, 0};
        var end = i[0] + 1 < pairs ? at(REF_TARGETS, 2 * (i[0] + 1) + 1) : refCount();
        return new int[] {at(REF_TARGETS, 2 * i[0] + 1), end};
    }

    /** The keys declared in these files (paths as stored, relative to the project). */
    public Set<String> declKeysIn(Collection<String> rels) {
        var files = rels.stream().mapToInt(this::find).filter(id -> id >= 0).boxed().collect(Collectors.toSet());
        var out = new LinkedHashSet<String>();
        for (var r = 0; r < declCount(); r++)
            if (files.contains(at(DECLS, r * DECL + D_FILE))) out.add(str(at(DECLS, r * DECL + D_KEY)));
        return out;
    }

    /** The files holding a reference to any of these keys. */
    public Set<String> filesReferencing(Collection<String> keys) {
        var out = new LinkedHashSet<String>();
        for (var k : keys) {
            var r = refRange(k);
            for (var i = r[0]; i < r[1]; i++) out.add(str(at(REFS, i * REF + R_FILE)));
        }
        return out;
    }

    public PackMeta meta() {
        return new PackMeta(Tier.values()[head(H_TIER)], str(head(H_ROOT)), str(head(H_COORDS)),
                str(head(H_CLASSPATH)), str(head(H_NOTES)), head(H_FILES), head(H_DECLS), head(H_REFS),
                head(H_ERRORS), head(H_UNRESOLVED), head(H_FORMAT),
                ((long) head(H_INDEX_MS) << 32) | (head(H_INDEX_MS + 1) & 0xFFFFFFFFL),
                ((long) head(H_INDEXED_AT) << 32) | (head(H_INDEXED_AT + 1) & 0xFFFFFFFFL));
    }

    Decl decl(int row, Path root) {
        var b = row * DECL;
        var kind = at(DECLS, b + D_KIND);
        var mods = str(at(DECLS, b + D_MODIFIERS));
        var list = at(DECLS, b + D_PARAMS);
        var params = new ArrayList<String>();
        if (list >= 0) for (var i = 0; i < at(PARAMS, list); i++) params.add(str(at(PARAMS, list + 1 + i)));
        return new Decl(str(at(DECLS, b + D_KEY)), DeclKind.values()[kind & (IMPLICIT - 1)],
                str(at(DECLS, b + D_NAME)), str(at(DECLS, b + D_OWNER)), abs(root, str(at(DECLS, b + D_FILE))),
                at(DECLS, b + D_LINE), at(DECLS, b + D_COL), at(DECLS, b + D_END), str(at(DECLS, b + D_SIGNATURE)),
                mods == null ? "" : mods, str(at(DECLS, b + D_RETURN)), params, List.of(),
                str(at(DECLS, b + D_DOC)), (kind & IMPLICIT) != 0);
    }

    Occurrence ref(int row, Path root) {
        var b = row * REF;
        return new Occurrence(str(at(REFS, b + R_TARGET)), abs(root, str(at(REFS, b + R_FILE))),
                at(REFS, b + R_LINE), at(REFS, b + R_COL), RefKind.values()[at(REFS, b + R_KIND)],
                str(at(REFS, b + R_ENCLOSING)));
    }

    static String abs(Path root, String rel) { return rel == null ? null : Src.abs(root, rel).toString(); }

    /** Every row again, paths as stored (relative to the project), so a pass can keep what did not change. */
    public PackData toData() {
        var decls = new ArrayList<Decl>();
        for (var i = 0; i < declCount(); i++) decls.add(rawDecl(i));
        var refs = new ArrayList<Occurrence>();
        for (var i = 0; i < refCount(); i++) refs.add(rawRef(i));
        var supers = new ArrayList<SuperEdge>();
        for (var i = 0; i < count(SUPERS, SUPER); i++)
            supers.add(new SuperEdge(str(at(SUPERS, i * SUPER)), str(at(SUPERS, i * SUPER + 1)),
                    at(SUPERS, i * SUPER + 2) == 1));
        return new PackData(meta(), decls, refs, supers, stamps());
    }

    Decl rawDecl(int row) {
        var d = decl(row, Path.of(""));
        var rel = str(at(DECLS, row * DECL + D_FILE));
        return new Decl(d.key(), d.kind(), d.name(), d.ownerKey(), rel, d.line(), d.col(), d.endLine(),
                d.signature(), d.modifiers(), d.returnType(), d.paramTypes(), d.superKeys(), d.doc(), d.implicit());
    }

    Occurrence rawRef(int row) {
        var o = ref(row, Path.of(""));
        return new Occurrence(o.targetKey(), str(at(REFS, row * REF + R_FILE)), o.line(), o.col(), o.kind(),
                o.enclosingKey());
    }

    public List<FileStamp> stamps() {
        var out = new ArrayList<FileStamp>();
        for (var i = 0; i < count(FILES, FILE); i++) {
            var b = i * FILE;
            out.add(new FileStamp(str(at(FILES, b)), ((long) at(FILES, b + 1) << 32) | (at(FILES, b + 2) & 0xFFFFFFFFL),
                    ((long) at(FILES, b + 3) << 32) | (at(FILES, b + 4) & 0xFFFFFFFFL)));
        }
        return out;
    }
}
