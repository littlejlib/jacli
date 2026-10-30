package littlejlib.jacli.index;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.pack.*;
import littlejlib.jacli.proj.*;
import littlejlib.jacli.query.*;

/**
 * Keeps a project's index current and hands out a store to query it. Holds a warm {@link Analyzer}
 * per project for the passes that have to read source again, which is where a cold javac pass spends
 * its time: reading the dependency jars. A reused file manager does not pay that twice. Answering a
 * query needs no javac at all, only the project's pack.
 */
public final class ProjectIndex {

    final Catalog catalog = Catalog.get();
    final Map<String, Analyzer> warm = new HashMap<>();
    Freshness freshness = Freshness.ALWAYS_SWEEP;

    /**
     * Lets the daemon vouch for a project's freshness so a query need not sweep for it.
     * Without a watcher this stays {@link Freshness#ALWAYS_SWEEP}, which is the behaviour every query
     * had before: correct, just paying ~100ms per question on a large tree.
     */
    public void freshness(Freshness f) { this.freshness = f == null ? Freshness.ALWAYS_SWEEP : f; }

    /** Releases the warm javac file manager for a project the daemon has stopped watching. */
    public synchronized void release(String pid) {
        var keys = warm.keySet().stream().filter(k -> k.startsWith(pid + "|")).toList();
        keys.forEach(k -> warm.remove(k).close());
        Packs.release(pid);
    }

    /**
     * A store over several projects at once. Each is indexed and kept fresh exactly as a single project
     * is; only the reading is widened. Paths are already absolute as rows leave each store, so a hit
     * from a sibling project cannot be resolved against the wrong root.
     */
    public Store ensureScope(List<Path> roots, String scopeName, boolean includeTests, boolean force,
                             Consumer<String> log) {
        var stores = new ArrayList<Store>();
        for (var root : roots) {
            try {
                stores.add(ensure(root, includeTests, force, log));
            } catch (RuntimeException e) {
                log.accept("scope member " + root + " skipped: " + e.getMessage());
            }
        }
        if (stores.isEmpty()) throw new IllegalStateException("no project in scope '" + scopeName
                + "' could be indexed (tried " + roots + ")");
        return stores.size() == 1 ? stores.getFirst() : new MultiStore(stores, scopeName);
    }

    /** The scope group a project belongs to, so an answer can say a wider one is available. */
    public String groupOf(Path where) { return catalog.groupOf(Home.token(Projects.findRoot(where))); }

    public List<Path> groupRoots(String group) {
        return catalog.inGroup(group).stream().map(JProject::root).filter(Objects::nonNull)
                .map(Path::of).filter(Files::isDirectory).toList();
    }

    public Store ensure(Path where, boolean includeTests, boolean force, Consumer<String> log) {
        /* The whole point of watching: when something is holding a live watch and has swept since the
         * last event, skip re-walking the tree, re-resolving the classpath and re-fingerprinting every
         * file. On a 488-file project that is most of the query's cost. */
        if (!force && !includeTests) {
            var vouched = freshness.verifiedProject(Home.token(Projects.findRoot(where)));
            if (vouched != null) {
                var row = catalog.project(vouched.id());
                if (row != null && row.formatVersion() != null && row.formatVersion() >= JProject.FORMAT) {
                    var pack = Packs.read(vouched.id());
                    if (pack.isPresent()) return store(vouched, pack.get(), row, true, "");
                }
            }
        }
        /* Announced BEFORE Projects.open, because resolving a cold maven classpath is itself seconds of
         * the wait and happens before there is a file count to report. */
        var root = Projects.findRoot(where);
        if (catalog.project(Home.token(root)) == null)
            Progress.note("first call in " + root + " - resolving its build and indexing it. Calls after this one are ~0.3s.");
        var project = Projects.open(where, includeTests, false, log);
        if (project.javaFiles().isEmpty())
            throw new IllegalStateException("no java sources found under " + project.root()
                    + " (looked in " + project.sourceRoots() + ")");
        var pid = project.id();
        var now = Fingerprint.of(project.root(), project.javaFiles());
        var row = catalog.project(pid);
        var old = Packs.read(pid);

        if (force || row == null || old.isEmpty()) return full(project, now, log, force ? "forced" : "first index");
        if (row.formatVersion() == null || row.formatVersion() < JProject.FORMAT)
            return full(project, now, log, "index format " + row.formatVersion() + " predates "
                    + JProject.FORMAT + ", so it was built by code that read the sources differently");
        var changes = Fingerprint.diff(now, storedStamps(old.get()));
        if (changes.isEmpty()) {
            freshness.vouch(pid, project);
            return store(project, old.get(), row, true, "");
        }
        if (!project.classpath().hasCompiledOutput()) {
            log.accept(changes + " but no compiled output on the classpath, so a single-file pass could not "
                    + "resolve project-internal types - reindexing the whole project");
            return full(project, now, log, changes.toString());
        }
        return incremental(project, old.get(), now, changes, log);
    }

    Store full(JavaProject project, Map<String, FileStamp> stamps, Consumer<String> log, String why) {
        log.accept("indexing " + project.fileCount() + " files (" + why + ")");
        Progress.note("compiling " + project.fileCount() + " java files (" + why + ")");
        var t0 = System.nanoTime();
        var a = analyzer(project).analyze(project.javaFiles());
        var ms = (System.nanoTime() - t0) / 1_000_000;
        var meta = new PackMeta(a.tier(), project.root().toString(), project.coords().toString(),
                project.classpath().source(), String.join(" | ", project.classpath().notes()), stamps.size(),
                a.decls().size(), a.refs().size(), a.errors(), a.unresolved(), JProject.FORMAT, ms,
                System.currentTimeMillis());
        log.accept(a.tier() + " " + a.timings());
        return saved(project, new PackData(meta, a.decls(), a.refs(), a.supers(), List.copyOf(stamps.values())));
    }

    public static final int MAX_DEPENDENT_KEYS = 400;
    public static final double FULL_REINDEX_FRACTION = 0.5;

    /**
     * Re-analyses the changed files AND the files that reference what they declared.
     * <p>
     * Without that second set the index goes quietly wrong exactly when you are refactoring: rename
     * {@code Foo.bar()} and only Foo.java is re-read, so an untouched caller keeps a row pointing at
     * the old key — {@code refs Foo.baz()} misses the call and {@code refs Foo.bar()} reports one that
     * no longer exists, both while the tier still reads RESOLVED because the file that WAS re-read
     * compiled cleanly. The reference table is what makes this affordable: it already knows who
     * refers to what.
     */
    Store incremental(JavaProject project, Pack old, Map<String, FileStamp> stamps, Changes changes,
                      Consumer<String> log) {
        var touched = new ArrayList<String>(changes.changed());
        touched.addAll(changes.removed());
        var keys = old.declKeysIn(touched);
        if (keys.size() > MAX_DEPENDENT_KEYS)
            return full(project, stamps, log, changes + ", declaring too many symbols to trace dependents");
        var dependents = new LinkedHashSet<>(old.filesReferencing(keys));
        dependents.removeAll(touched);

        var affected = new LinkedHashSet<>(changes.changed());
        affected.addAll(dependents);
        changes.removed().forEach(affected::remove);
        if (affected.size() > Math.max(8, project.fileCount() * FULL_REINDEX_FRACTION))
            return full(project, stamps, log, changes + ", and " + affected.size()
                    + " files depend on what changed, which is most of the project");

        log.accept("reindexing " + changes + (dependents.isEmpty() ? ""
                : " plus " + dependents.size() + " referencing " + (dependents.size() == 1 ? "file" : "files")));
        var files = affected.stream().map(rel -> Src.abs(project.root(), rel)).filter(Files::isRegularFile).toList();
        var a = analyzer(project).analyze(files);
        if (a.tier() != Tier.RESOLVED)
            log.accept("incremental pass came back " + a.tier() + "; run --reindex if the answer looks wrong");
        return saved(project, PackMerge.apply(old.toData(), affected, changes.removed(), a, stamps));
    }

    /** Writes the pack, updates the catalog row (keeping what the user set on it) and answers from the new pack. */
    Store saved(JavaProject project, PackData data) {
        var pid = project.id();
        var bytes = PackWriter.write(data);
        Packs.write(pid, bytes);
        var m = data.meta();
        var row = catalog.update(pid, r -> r.root(m.root()).coords(m.coords()).tier(m.tier().name())
                .classpathSource(m.classpathSource()).indexedAt(m.indexedAt()).indexMs(m.indexMs())
                .files(m.files()).declCount(m.declCount()).refCount(m.refCount()).javacErrors(m.javacErrors())
                .unresolved(m.unresolved()).formatVersion(m.formatVersion()).notes(m.notes())
                .watched(Boolean.TRUE.equals(r.watched())));
        freshness.vouch(pid, project);
        return store(project, Pack.of(bytes), row, true, "");
    }

    synchronized Analyzer analyzer(JavaProject project) {
        return warm.computeIfAbsent(project.id() + "|" + project.classpath().classpath(),
                k -> new Analyzer(project.root(), project.classpath().classpath(), List.of()));
    }

    static Map<String, FileStamp> storedStamps(Pack pack) {
        var out = new HashMap<String, FileStamp>();
        pack.stamps().forEach(s -> out.put(s.rel(), s));
        return out;
    }

    Store store(JavaProject project, Pack pack, JProject row, boolean fresh, String staleness) {
        return new PackStore(pack, project.root(), meta(project, row, fresh, staleness));
    }

    static Meta meta(JavaProject p, JProject row, boolean fresh, String staleness) {
        /* The stored row's notes are the joined notes of the indexing run, so they usually repeat what
         * live classpath resolution just said. Split and dedupe rather than printing both. */
        var notes = new LinkedHashSet<>(p.classpath().notes());
        if (row != null && row.notes() != null && !row.notes().isBlank())
            Arrays.stream(row.notes().split(" \\| ")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(notes::add);
        return new Meta(p.coords().toString(), p.root(), tier(row), num(row == null ? null : row.files()),
                num(row == null ? null : row.declCount()), num(row == null ? null : row.refCount()),
                num(row == null ? null : row.javacErrors()), num(row == null ? null : row.unresolved()),
                p.classpath().source(), fresh, staleness, List.copyOf(notes));
    }

    static Tier tier(JProject row) {
        if (row == null || row.tier() == null) return Tier.SYNTACTIC;
        try {
            return Tier.valueOf(row.tier());
        } catch (IllegalArgumentException e) {
            return Tier.SYNTACTIC;
        }
    }

    static int num(Integer i) { return i == null ? 0 : i; }

    public synchronized void closeWarm() {
        warm.values().forEach(Analyzer::close);
        warm.clear();
    }
}
