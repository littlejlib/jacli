package littlejlib.jacli.proj.gradle;

import module java.base;
import littlejlib.jacli.proj.Coords;

/**
 * What jacli needs to know about a gradle build, from whichever producer could supply it: gradle
 * itself through an init script, or the static reader when gradle cannot be run. Both fill the same
 * shape, so everything downstream is indifferent to which one answered — only {@link #source} says.
 * <p>
 * Serialised as tab-separated lines rather than json, because the producing end is a groovy init
 * script and a hand-rolled writer there is one fewer thing to go wrong than a dependency.
 */
public record GradleModel(Path root, Coords coords, List<GradleModule> modules, String source, List<String> notes) {

    public static final int VERSION = 1;

    /** The init script's wording when a project's classpath had to fall back to a lenient view. */
    public static final String INCOMPLETE = "compile classpath incomplete";

    /**
     * How many projects resolved incompletely. This is the signal the offline step cannot give through
     * its exit code: per-project resolution failures are caught so that one unresolvable subproject does
     * not cost the whole model, which means gradle exits 0 with a classpath full of holes.
     */
    public long incomplete() {
        return notes.stream().filter(n -> n.contains(INCOMPLETE)).count();
    }

    public GradleModel withNote(String note) {
        var all = new ArrayList<>(notes);
        all.add(note);
        return new GradleModel(root, coords, modules, source, List.copyOf(all));
    }

    public GradleModel withSource(String s) { return new GradleModel(root, coords, modules, s, notes); }

    /** Subproject directories, root excluded, in declaration order — the module roots the index uses. */
    public List<Path> moduleDirs() {
        return modules.stream().filter(m -> !m.isRoot())
                .map(GradleModule::dir).filter(Files::isDirectory).distinct().toList();
    }

    public List<Path> sourceRoots(boolean includeTests) {
        return modules.stream().flatMap(m -> m.sources(includeTests).stream())
                .filter(Files::isDirectory).distinct().toList();
    }

    public List<Path> outputDirs() {
        return modules.stream().flatMap(m -> m.output().stream()).distinct().toList();
    }

    /** Every jar any subproject compiles against, deduplicated, existing files only. */
    public String classpath() {
        var seen = new LinkedHashSet<String>();
        for (var m : modules)
            for (var entry : m.classpath())
                if (Files.exists(Path.of(entry))) seen.add(entry);
        return String.join(File.pathSeparator, seen);
    }

    /** Nearest-wins across the whole build, so one artifact appears once at its shallowest depth. */
    public List<GradleDep> deps(int maxDepth) {
        var best = new LinkedHashMap<String, GradleDep>();
        for (var m : modules)
            for (var d : m.deps()) {
                if (d.depth() > maxDepth) continue;
                var existing = best.get(d.key());
                if (existing == null || d.depth() < existing.depth()) best.put(d.key(), d);
            }
        return best.values().stream().sorted(Comparator.comparingInt(GradleDep::depth)
                .thenComparing(GradleDep::key)).toList();
    }

    public String render() {
        var sb = new StringBuilder("V\t" + VERSION + "\nR\t" + coords.groupId() + '\t' + coords.artifactId()
                + '\t' + coords.version() + "\n");
        notes.forEach(n -> sb.append("N\t").append(n.replace('\t', ' ')).append('\n'));
        for (var m : modules) {
            sb.append("M\t").append(m.path()).append('\t').append(m.dir()).append('\n');
            m.mainSrc().forEach(p -> sb.append("S\t").append(m.path()).append('\t').append(p).append('\n'));
            m.testSrc().forEach(p -> sb.append("T\t").append(m.path()).append('\t').append(p).append('\n'));
            m.output().forEach(p -> sb.append("O\t").append(m.path()).append('\t').append(p).append('\n'));
            m.classpath().forEach(p -> sb.append("C\t").append(m.path()).append('\t').append(p).append('\n'));
            m.deps().forEach(d -> sb.append("D\t").append(m.path()).append('\t').append(d.depth())
                    .append('\t').append(d.coords()).append('\n'));
        }
        return sb.toString();
    }

    public static GradleModel parse(Path root, String text, String source) {
        var coords = new Coords("unknown", root.getFileName().toString(), "0");
        var notes = new ArrayList<String>();
        var order = new ArrayList<String>();
        var dirs = new LinkedHashMap<String, Path>();
        var main = new LinkedHashMap<String, List<Path>>();
        var test = new LinkedHashMap<String, List<Path>>();
        var out = new LinkedHashMap<String, List<Path>>();
        var cp = new LinkedHashMap<String, List<String>>();
        var deps = new LinkedHashMap<String, List<GradleDep>>();
        for (var line : text.lines().toList()) {
            var f = line.split("\t", -1);
            if (f.length < 2) continue;
            switch (f[0]) {
                case "R" -> coords = f.length >= 4 ? new Coords(blankTo(f[1], "unknown"), f[2],
                        blankTo(f[3], "0")) : new Coords("unknown", f[1], "0");
                case "N" -> notes.add(f[1]);
                case "M" -> {
                    if (f.length < 3) continue;
                    order.add(f[1]);
                    dirs.put(f[1], Path.of(f[2]));
                }
                case "S" -> collect(main, f, Path::of);
                case "T" -> collect(test, f, Path::of);
                case "O" -> collect(out, f, Path::of);
                case "C" -> collect(cp, f, s -> s);
                case "D" -> {
                    if (f.length < 4) continue;
                    deps.computeIfAbsent(f[1], k -> new ArrayList<>())
                            .add(new GradleDep(parseInt(f[2]), Coords.parse(f[3])));
                }
                default -> { }
            }
        }
        var modules = order.stream().distinct().map(p -> new GradleModule(p, dirs.get(p),
                List.copyOf(main.getOrDefault(p, List.of())), List.copyOf(test.getOrDefault(p, List.of())),
                List.copyOf(out.getOrDefault(p, List.of())), List.copyOf(cp.getOrDefault(p, List.of())),
                List.copyOf(deps.getOrDefault(p, List.of())))).toList();
        return new GradleModel(root, coords, modules, source, List.copyOf(notes));
    }

    static String blankTo(String s, String fallback) {
        return s == null || s.isBlank() || "unspecified".equals(s) ? fallback : s;
    }

    static <T> void collect(Map<String, List<T>> into, String[] f, Function<String, T> of) {
        if (f.length < 3 || f[2].isBlank()) return;
        into.computeIfAbsent(f[1], k -> new ArrayList<>()).add(of.apply(f[2]));
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
