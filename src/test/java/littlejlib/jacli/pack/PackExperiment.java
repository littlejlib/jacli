package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.*;
import littlejlib.jacli.query.*;

/// Builds a pack from a real project's analysis, checks every Store answer against MemStore, and
/// prints size, load time and lookup speed.
/// mvn exec:java -Dexec.mainClass=littlejlib.jacli.pack.PackExperiment -Dexec.classpathScope=test -Dexec.args="<root>..."
public class PackExperiment {

    public static void main(String[] args) throws Exception {
        for (var arg : args) run(Path.of(arg));
        System.exit(0);
    }

    static void run(Path where) throws Exception {
        var project = Projects.open(where, false, false, s -> { });
        var root = project.root();
        var analysis = new Analyzer(root, project.classpath().classpath(), List.of()).analyze(project.javaFiles());
        var stamps = List.copyOf(Fingerprint.of(root, project.javaFiles()).values());
        var meta = new PackMeta(analysis.tier(), root.toString(), project.coords().toString(), "x", "", stamps.size(),
                analysis.decls().size(), analysis.refs().size(), analysis.errors(), analysis.unresolved(), 5, 1, 2);
        var data = new PackData(meta, analysis.decls(), analysis.refs(), analysis.supers(), stamps);

        var t0 = System.nanoTime();
        var bytes = PackWriter.write(data);
        var writeMs = (System.nanoTime() - t0) / 1e6;
        var file = Files.createTempFile("pack", ".bin");
        Files.write(file, bytes);
        t0 = System.nanoTime();
        var pack = Pack.of(Files.readAllBytes(file));
        var loadMs = (System.nanoTime() - t0) / 1e6;
        Files.delete(file);

        var m = new Meta("x", root, analysis.tier(), 0, 0, 0, 0, 0, "x", true, "", List.of());
        var mem = new MemStore(root, m, analysis.decls(), analysis.refs(), analysis.supers());
        var ps = new PackStore(pack, root, m);
        System.out.printf("%n== %s%n   %d files, %d decls, %d refs, %d supers%n", root, stamps.size(),
                analysis.decls().size(), analysis.refs().size(), analysis.supers().size());
        System.out.printf("   pack %,d bytes (write %.0f ms, read+open %.1f ms)%n", bytes.length, writeMs, loadMs);
        var problems = new Check(mem, ps).all(analysis);
        System.out.println("   equivalence: " + (problems == 0 ? "OK" : problems + " DIFFERENCES"));
        speed(mem, ps, analysis);
        heap(analysis, bytes, root, m);
    }

    static void speed(MemStore mem, PackStore ps, Analysis a) {
        var names = a.decls().stream().map(Decl::name).distinct().limit(300).toList();
        var targets = a.refs().stream().map(Occurrence::targetKey).distinct().limit(300).toList();
        for (var round = 0; round < 3; round++) {
            var t = System.nanoTime();
            var n = 0;
            for (var s : names) n += ps.declsByName(s).size();
            for (var k : targets) n += ps.refsTo(k).size();
            var pack = (System.nanoTime() - t) / 1e3 / (names.size() + targets.size());
            t = System.nanoTime();
            for (var s : names) n += mem.declsByName(s).size();
            for (var k : targets) n += mem.refsTo(k).size();
            var mm = (System.nanoTime() - t) / 1e3 / (names.size() + targets.size());
            if (round == 2) System.out.printf("   lookup: pack %.1f us, MemStore %.1f us (declsByName+refsTo, %d results)%n", pack, mm, n);
        }
    }

    static void heap(Analysis a, byte[] bytes, Path root, Meta m) {
        System.gc();
        var before = used();
        var mem = new MemStore(root, m, a.decls(), a.refs(), a.supers());
        System.gc();
        var withMem = used();
        System.out.printf("   heap: pack %,d bytes; MemStore adds about %,d bytes on top of the analysis%n",
                bytes.length, withMem - before);
        if (mem.declsByName("x") == null) System.out.println();
    }

    static long used() { var r = Runtime.getRuntime(); return r.totalMemory() - r.freeMemory(); }
}
