package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.core.Decompiler;
import littlejlib.jacli.daemon.Client;
import littlejlib.jacli.proj.*;
import xyz.jphil.arcadedb.datahelper.Indexes;
import xyz.jphil.arcadedb.datahelper.TypeDef;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "cache", description = "What jacli keeps on disk, and how to throw any of it away.",
        footer = {
            "",
            "  jacli cache                       what exists and how big it is",
            "  jacli cache --clear sources       extracted sources jars",
            "  jacli cache --clear git           cloned repositories",
            "  jacli cache --clear classpath     resolved classpaths, and gradle models with them",
            "  jacli cache --clear gradle        just the gradle models (re-asks gradle next time)",
            "  jacli cache --clear decompiled    decompiled bytecode",
            "  jacli cache --clear index         the whole index (stops the daemon first)",
            "  jacli cache --clear all           everything",
            "  jacli cache --forget <dir>        drop ONE project from the index, leaving the rest",
            "  jacli cache --repair              rebuild index structures from the records, losing nothing",
            "",
            "Nothing here is precious - every entry can be rebuilt from the sources or the repository.",
            "To rebuild one project's index instead of all of them, use --reindex on any verb.",
            "--forget is for an entry that should never have been indexed - typically a directory holding",
            "several sibling projects, which becomes one project with no build file. jacli projects marks",
            "those, and jacli discover <dir> lists the real roots underneath."
        })
public final class CacheCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Option(names = "--clear", paramLabel = "<what>", split = ",",
            description = "sources, git, classpath, decompiled, index, or all. Repeatable.")
    List<String> clear = new ArrayList<>();

    @Option(names = "--forget", paramLabel = "<dir>",
            description = "Drop just this project from the index, leaving every other project intact.")
    Path forget;

    @Option(names = "--repair",
            description = "Rebuild the index structures from the records. Fixes 'index rows could not be read'.")
    boolean repair;

    static final Map<String, String> DIRS = new LinkedHashMap<>(Map.of(
            "index", "db", "sources", Targets.SRC_CACHE, "git", Targets.GIT_CACHE,
            "classpath", "cp", "gradle", littlejlib.jacli.proj.gradle.Gradle.CACHE,
            "decompiled", Decompiler.CACHE));

    @Override
    public Integer call() {
        var out = new StringBuilder();
        if (repair) return doRepair(out);
        if (forget != null) return doForget(out);
        if (!clear.isEmpty()) return doClear(out);
        out.append("# jacli caches under ").append(Home.dir()).append('\n');
        DIRS.forEach((name, dir) -> {
            var path = Home.dir().resolve(dir);
            var size = sizeOf(path);
            out.append(String.format("  %-12s %-14s %8s  %s%n", name, dir,
                    size < 0 ? "-" : human(size), Files.isDirectory(path) ? "" : "(absent)"));
        });
        out.append("# clear any of them with --clear <name>, or --clear all\n");
        common.emit(out.toString());
        return 0;
    }

    int doClear(StringBuilder out) {
        var wanted = clear.contains("all") ? new ArrayList<>(DIRS.keySet())
                : new ArrayList<>(clear.stream().map(String::toLowerCase).distinct().toList());
        /* A gradle project's classpath IS its model, so asking for the classpath and keeping the model
         * would leave the thing the caller wanted rebuilt exactly as it was. */
        if (wanted.contains("classpath") && !wanted.contains("gradle")) wanted.add("gradle");
        var unknown = wanted.stream().filter(w -> !DIRS.containsKey(w)).toList();
        if (!unknown.isEmpty()) {
            System.err.println("jacli: unknown cache " + unknown + "; choose from " + DIRS.keySet() + " or all");
            return littlejlib.jacli.query.Answer.USAGE;
        }
        /* The daemon holds the index open with an exclusive lock, so deleting it underneath would
         * corrupt what the daemon still believes it has. Stop it first and let the next verb restart it. */
        if (wanted.contains("index") && Client.ping() != null) {
            out.append("# stopping the daemon so the index can be removed\n");
            Client.stop(s -> out.append("# ").append(s).append('\n'));
        }
        for (var name : wanted) {
            var path = Home.dir().resolve(DIRS.get(name));
            var before = sizeOf(path);
            var removed = deleteTree(path);
            out.append("  cleared ").append(name).append(" (").append(removed)
                    .append(removed == 1 ? " file, " : " files, ").append(before < 0 ? "0" : human(before))
                    .append(")\n");
        }
        out.append("# gone. Everything here rebuilds itself on the next query.\n");
        common.emit(out.toString());
        return 0;
    }

    /**
     * Drops every index structure and builds it again from the records that are there.
     * <p>
     * This is the ONE repair for an index entry whose record has gone. Nothing else clears it: the
     * entry has no record behind it, so a delete cannot find it and a reindex writes new entries beside
     * it — a forced reindex of an affected project returns identical skip counts. Nothing indexed is
     * lost, because every entry is derived from a record that still exists.
     */
    int doRepair(StringBuilder out) {
        var db = littlejlib.jacli.index.Db.get().db();
        try {
            var names = littlejlib.jacli.index.Db.typeNames();
            var before = names.stream().mapToLong(n -> Indexes.orphans(db, n)).sum();
            out.append("# rebuilding index structures over ").append(names.size())
                    .append(" types").append(before == 0 ? " (none currently unreadable)"
                            : " (" + before + " unreadable entries found)").append('\n');
            var t0 = System.nanoTime();
            var rebuilt = Indexes.rebuild(db, littlejlib.jacli.index.Db.TYPES.toArray(TypeDef[]::new));
            var after = names.stream().mapToLong(n -> Indexes.orphans(db, n)).sum();
            out.append("# ").append(rebuilt).append(" indexes rebuilt in ")
                    .append((System.nanoTime() - t0) / 1_000_000).append("ms; unreadable entries now ")
                    .append(after).append('\n');
            out.append(after == 0 ? "# the index and the records agree again. Nothing was reindexed.\n"
                    : "# still unreadable, which this cannot explain. Fall back to --clear index.\n");
            common.emit(out.toString());
            return after == 0 ? 0 : 1;
        } finally {
            if (littlejlib.jacli.index.ServerContext.index() == null) littlejlib.jacli.index.Db.shutdown();
        }
    }

    /**
     * Matches on the stored root first, because that is the string {@code jacli projects} printed and so
     * the one the reader will paste back. Resolving through {@link Projects#findRoot} instead would walk
     * up out of the very directory being named whenever a parent happens to carry a pom.
     */
    int doForget(StringBuilder out) {
        var db = littlejlib.jacli.index.Db.get().db();
        try {
            if (!Files.exists(forget))
                out.append("# ").append(forget.toAbsolutePath()).append(" does not exist; looking it up anyway, "
                        + "since a project whose directory is gone is exactly one worth forgetting\n");
            var target = forget.toAbsolutePath().normalize();
            var row = littlejlib.jacli.index.Counts.all(db).stream()
                    .filter(p -> p.root() != null && Path.of(p.root()).equals(target)).findFirst()
                    .orElseGet(() -> littlejlib.jacli.index.Counts.project(db, Home.token(Projects.findRoot(forget))));
            if (row == null) {
                System.err.println("jacli: no indexed project at " + target + " - jacli projects lists what is indexed");
                return littlejlib.jacli.query.Answer.NOT_INDEXED;
            }
            var shared = littlejlib.jacli.index.ServerContext.index();
            if (shared != null) shared.release(row.projectId());
            littlejlib.jacli.index.Counts.forget(db, row.projectId());
            out.append("# forgot ").append(row.coords()).append("  ").append(row.root()).append('\n')
                    .append("# ").append(row.files()).append(" files, ").append(row.declCount())
                    .append(" declarations and ").append(row.refCount()).append(" references dropped. ")
                    .append("Every other project is untouched.\n");
            common.emit(out.toString());
            return 0;
        } finally {
            if (littlejlib.jacli.index.ServerContext.index() == null) littlejlib.jacli.index.Db.shutdown();
        }
    }

    static long sizeOf(Path dir) {
        if (!Files.isDirectory(dir)) return -1;
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).mapToLong(CacheCmd::lengthOf).sum();
        } catch (IOException e) {
            return -1;
        }
    }

    static long lengthOf(Path f) {
        try {
            return Files.size(f);
        } catch (IOException e) {
            return 0;
        }
    }

    static int deleteTree(Path dir) {
        if (!Files.exists(dir)) return 0;
        var count = new int[1];
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    if (Files.isRegularFile(p)) count[0]++;
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    System.err.println("jacli: could not delete " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("jacli: could not walk " + dir + ": " + e.getMessage());
        }
        return count[0];
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
}
