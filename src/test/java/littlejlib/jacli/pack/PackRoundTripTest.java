package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.FileStamp;
import littlejlib.jacli.query.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackRoundTripTest {

    static final Path ROOT = Path.of("C:/proj").toAbsolutePath();

    static Decl type(String key, String name, String file, int line) {
        return new Decl(key, DeclKind.CLASS, name, null, file, line, 1, line + 5, "class " + name, "public", null,
                List.of(), List.of(), null);
    }

    static Decl method(String owner, String name, String file, int line, List<String> params, String doc) {
        return new Decl(owner + "#" + name + "(" + String.join(",", params) + ")", DeclKind.METHOD, name, owner, file,
                line, 5, line, name + "()", "", "void", params, List.of(), doc, false);
    }

    static Analysis analysis() {
        var a = type("p.A", "A", "src/A.java", 3);
        var b = type("p.B", "B", "src/B.java", 4);
        var run = method("p.A", "run", "src/A.java", 6, List.of("int", "java.lang.String"), "ünïcode — doc");
        var gen = new Decl("p.A#<init>()", DeclKind.CONSTRUCTOR, "A", "p.A", "src/A.java", 3, 1, 3, "A()", "", null,
                List.of(), List.of(), null, true);
        var refs = List.of(
                new Occurrence("p.A#run(int,java.lang.String)", "src/B.java", 9, 3, RefKind.CALL, "p.B"),
                new Occurrence("p.A", "src/B.java", 4, 20, RefKind.TYPE, null),
                new Occurrence(Keys.unresolved("mystery"), "src/B.java", 11, 1, RefKind.TYPE, "p.B"));
        return new Analysis(Tier.PARTIAL, List.of(a, b, run, gen), refs, List.of(new SuperEdge("p.B", "p.A", true),
                new SuperEdge("p.B", "java.lang.Object", false)), 2, 1, List.of(), 1, 1, 1, 1);
    }

    static PackData data(Analysis a, List<FileStamp> stamps) {
        return new PackData(new PackMeta(a.tier(), ROOT.toString(), "g:a:1", "maven", "n1 | n2", stamps.size(),
                a.decls().size(), a.refs().size(), a.errors(), a.unresolved(), 6, 12L, 3_000_000_000L), a.decls(),
                a.refs(), a.supers(), stamps);
    }

    static final List<FileStamp> STAMPS = List.of(new FileStamp("src/A.java", 5_000_000_000L, 7), new FileStamp("src/B.java", 2, 8));

    @Test
    void everyStoreAnswerMatchesTheInMemoryStore() {
        var a = analysis();
        var pack = Pack.of(PackWriter.write(data(a, STAMPS)));
        var meta = new Meta("x", ROOT, a.tier(), 0, 0, 0, 0, 0, "x", true, "", List.of());
        var mem = new MemStore(ROOT, meta, a.decls(), a.refs(), a.supers());
        assertEquals(0, new Check(mem, new PackStore(pack, ROOT, meta)).all(a));
    }

    @Test
    void metaAndStampsSurviveWithLargeValues() {
        var m = Pack.of(PackWriter.write(data(analysis(), STAMPS))).meta();
        assertEquals(Tier.PARTIAL, m.tier());
        assertEquals("n1 | n2", m.notes());
        assertEquals(3_000_000_000L, m.indexedAt());
        assertEquals(STAMPS, Pack.of(PackWriter.write(data(analysis(), STAMPS))).stamps());
    }

    @Test
    void absentSpellingsAnswerEmptyNotWrong() {
        var a = analysis();
        var meta = new Meta("x", ROOT, a.tier(), 0, 0, 0, 0, 0, "x", true, "", List.of());
        var store = new PackStore(Pack.of(PackWriter.write(data(a, STAMPS))), ROOT, meta);
        assertTrue(store.declsByName("zzz").isEmpty());
        assertTrue(store.refsTo("p.Nope").isEmpty());
        assertEquals(0, store.refCount("p.Nope"));
        assertTrue(store.supersOf("p.Nope").isEmpty());
    }

    @Test
    void anIncrementalPassKeepsOtherFilesAndReplacesTheRedoneOnes() {
        var a = analysis();
        var old = data(a, STAMPS);
        var again = new Analysis(Tier.RESOLVED, List.of(type("p.B", "B", "src/B.java", 40)),
                List.of(new Occurrence("p.A", "src/B.java", 40, 2, RefKind.TYPE, null)), List.of(), 1, 0, List.of(), 0, 0, 0, 0);
        var merged = PackMerge.apply(old, Set.of("src/B.java"), List.of(), again,
                Map.of("src/B.java", new FileStamp("src/B.java", 99, 100)));
        var pack = Pack.of(PackWriter.write(merged));
        assertEquals(Set.of("p.A", "p.A#<init>()", "p.A#run(int,java.lang.String)", "p.B"),
                pack.toData().decls().stream().map(Decl::key).collect(Collectors.toSet()));
        assertEquals(1, pack.toData().refs().size());
        assertTrue(pack.toData().supers().isEmpty(), "B's supertype edges go with B's declarations");
        assertEquals(99, pack.stamps().stream().filter(s -> s.rel().equals("src/B.java")).findFirst().orElseThrow().size());
        assertEquals(Set.of("src/B.java"), pack.filesReferencing(Set.of("p.A")));
        assertEquals(Set.of("p.A", "p.A#<init>()", "p.A#run(int,java.lang.String)"), pack.declKeysIn(Set.of("src/A.java")));
    }
}
