package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import static littlejlib.jacli.pack.Layout.*;

/// Turns the rows of one project into the bytes of a pack: a sorted string pool, the row tables, and a
/// few sorted permutations so every lookup the verbs make is a binary search over the file itself.
public final class PackWriter {

    final PackData d;
    final String[] strings;
    final Map<String, Integer> ids = new HashMap<>();
    final byte[][] bytes;

    PackWriter(PackData d) {
        this.d = d;
        var all = new HashSet<String>();
        collect(all);
        var encoded = all.stream().map(s -> new Encoded(s, s.getBytes(StandardCharsets.UTF_8)))
                .sorted((x, y) -> Arrays.compareUnsigned(x.bytes, y.bytes)).toList();
        strings = new String[encoded.size()];
        bytes = new byte[strings.length][];
        for (var i = 0; i < strings.length; i++) {
            strings[i] = encoded.get(i).text;
            bytes[i] = encoded.get(i).bytes;
            ids.put(strings[i], i);
        }
    }

    public static byte[] write(PackData d) { return new PackWriter(d).bytes(); }

    record Encoded(String text, byte[] bytes) { }

    void collect(Set<String> all) {
        var m = d.meta();
        for (var s : new String[] {m.root(), m.coords(), m.classpathSource(), m.notes()}) add(all, s);
        for (var x : d.decls()) {
            for (var s : new String[] {x.key(), x.name(), x.ownerKey(), x.file(), x.signature(), x.modifiers(),
                    x.returnType(), x.doc()}) add(all, s);
            x.paramTypes().forEach(s -> add(all, s));
        }
        for (var r : d.refs()) {
            for (var s : new String[] {r.targetKey(), r.file(), r.enclosingKey()}) add(all, s);
            add(all, simpleName(r.targetKey()));
        }
        for (var e : d.supers()) { add(all, e.childKey()); add(all, e.superKey()); }
        for (var f : d.stamps()) add(all, f.rel());
    }

    static void add(Set<String> all, String s) { if (s != null) all.add(s); }

    static String simpleName(String targetKey) { return SymbolKey.simpleNameOf(Keys.spelling(targetKey)); }

    int id(String s) { return s == null ? -1 : ids.get(s); }

    byte[] bytes() {
        var sections = new int[SECTIONS][];
        var params = new Ints();
        sections[DECLS] = decls(params);
        sections[PARAMS] = params.toArray();
        sections[DECL_BY_NAME] = perm(sections[DECLS], DECL, D_NAME);
        sections[DECL_BY_KEY] = perm(sections[DECLS], DECL, D_KEY);
        sections[DECL_BY_OWNER] = perm(sections[DECLS], DECL, D_OWNER);
        refs(sections);
        supers(sections);
        sections[FILES] = files();
        sections[STR_OFFSETS] = new int[strings.length + 1];
        var at = 0;
        for (var i = 0; i < strings.length; i++) { sections[STR_OFFSETS][i] = at; at += bytes[i].length; }
        sections[STR_OFFSETS][strings.length] = at;
        var header = new int[HEADER];
        var pos = HEADER;
        for (var s = 0; s < SECTIONS; s++) {
            header[H_SECTIONS + 2 * s] = pos;
            header[H_SECTIONS + 2 * s + 1] = sections[s].length;
            pos += sections[s].length;
        }
        meta(header, at);
        var out = ByteBuffer.allocate(pos * 4 + at);
        for (var x : header) out.putInt(x);
        for (var s : sections) for (var x : s) out.putInt(x);
        for (var b : bytes) out.put(b);
        return out.array();
    }

    void meta(int[] h, int strBytes) {
        var m = d.meta();
        h[H_MAGIC] = MAGIC; h[H_VERSION] = VERSION; h[H_TIER] = m.tier().ordinal(); h[H_FILES] = m.files();
        h[H_DECLS] = m.declCount(); h[H_REFS] = m.refCount(); h[H_ERRORS] = m.javacErrors();
        h[H_UNRESOLVED] = m.unresolved(); h[H_FORMAT] = m.formatVersion();
        h[H_INDEX_MS] = (int) (m.indexMs() >>> 32); h[H_INDEX_MS + 1] = (int) m.indexMs();
        h[H_INDEXED_AT] = (int) (m.indexedAt() >>> 32); h[H_INDEXED_AT + 1] = (int) m.indexedAt();
        h[H_COORDS] = id(m.coords()); h[H_ROOT] = id(m.root()); h[H_CLASSPATH] = id(m.classpathSource());
        h[H_NOTES] = id(m.notes()); h[H_STR_BYTES] = strBytes;
    }

    int[] decls(Ints params) {
        var out = new Ints();
        for (var x : d.decls()) {
            var list = -1;
            if (!x.paramTypes().isEmpty()) {
                list = params.n;
                params.add(x.paramTypes().size());
                x.paramTypes().forEach(p -> params.add(id(p)));
            }
            out.add(id(x.key())).add(x.kind().ordinal() | (x.implicit() ? IMPLICIT : 0)).add(id(x.name())).add(id(x.ownerKey())).add(id(x.file()))
                    .add(x.line()).add(x.col()).add(x.endLine()).add(id(x.signature())).add(id(x.modifiers()))
                    .add(id(x.returnType())).add(list).add(id(x.doc()));
        }
        return out.toArray();
    }

    /** Row numbers of a table, ordered by one field, leaving out rows where that field is absent. */
    static int[] perm(int[] rows, int stride, int field) {
        var keys = new long[rows.length / stride];
        var n = 0;
        for (var r = 0; r < keys.length; r++) {
            var v = rows[r * stride + field];
            if (v >= 0) keys[n++] = ((long) v << 32) | r;
        }
        keys = Arrays.copyOf(keys, n);
        Arrays.sort(keys);
        var out = new int[n];
        for (var i = 0; i < n; i++) out[i] = (int) keys[i];
        return out;
    }

    void refs(int[][] sections) {
        var refs = d.refs();
        var order = new long[refs.size()];
        for (var i = 0; i < order.length; i++) order[i] = ((long) id(refs.get(i).targetKey()) << 32) | i;
        Arrays.sort(order);
        var rows = new Ints();
        var targets = new Ints();
        var names = new TreeSet<Long>();
        var last = -2;
        for (var i = 0; i < order.length; i++) {
            var o = refs.get((int) order[i]);
            var t = id(o.targetKey());
            if (t != last) { targets.add(t).add(i); last = t; names.add(((long) id(simpleName(o.targetKey())) << 32) | t); }
            rows.add(t).add(id(o.file())).add(o.line()).add(o.col()).add(o.kind().ordinal()).add(id(o.enclosingKey()));
        }
        sections[REFS] = rows.toArray();
        sections[REF_TARGETS] = targets.toArray();
        sections[REF_BY_ENCLOSING] = perm(sections[REFS], REF, R_ENCLOSING);
        var pairs = new Ints();
        names.forEach(p -> pairs.add((int) (p >>> 32)).add((int) (long) p));
        sections[TARGET_NAMES] = pairs.toArray();
    }

    void supers(int[][] sections) {
        var seen = new HashSet<Long>();
        var order = new ArrayList<Long>();
        var direct = new HashMap<Long, Integer>();
        for (var e : d.supers()) {
            var key = ((long) id(e.superKey()) << 32) | id(e.childKey());
            if (seen.add(key)) { order.add(key); direct.put(key, e.direct() ? 1 : 0); }
        }
        Collections.sort(order);
        var rows = new Ints();
        for (var k : order) rows.add((int) (long) k).add((int) (k >>> 32)).add(direct.get(k));
        sections[SUPERS] = rows.toArray();
        sections[SUPER_BY_CHILD] = perm(sections[SUPERS], SUPER, 0);
    }

    int[] files() {
        var stamps = new ArrayList<>(d.stamps());
        stamps.sort(Comparator.comparingInt(s -> id(s.rel())));
        var out = new Ints();
        for (var s : stamps)
            out.add(id(s.rel())).add((int) (s.size() >>> 32)).add((int) s.size())
                    .add((int) (s.mtime() >>> 32)).add((int) s.mtime());
        return out.toArray();
    }
}
