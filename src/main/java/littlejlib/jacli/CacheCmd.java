package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.core.Decompiler;
import littlejlib.jacli.daemon.Client;
import littlejlib.jacli.proj.*;
import littlejlib.jacli.index.Catalog;
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
            "  jacli cache --clear index         every project's index file (scopes and pins are kept)",
            "  jacli cache --clear olddb         the database the index used before it became one file per project",
            "  jacli cache --clear all           everything",
            "  jacli cache --forget <dir>        drop ONE project from the index, leaving the rest",
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

    static final Map<String, String> DIRS = new LinkedHashMap<>(Map.of(
            "index", "packs", "olddb", "db", "sources", Targets.SRC_CACHE, "git", Targets.GIT_CACHE,
            "classpath", "cp", "gradle", littlejlib.jacli.proj.gradle.Gradle.CACHE,
            "decompiled", Decompiler.CACHE));

    @Override
    public Integer call() {
        var out = new StringBuilder();
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
     * Matches on the stored root first, because that is the string {@code jacli projects} printed and so
     * the one the reader will paste back. Resolving through {@link Projects#findRoot} instead would walk
     * up out of the very directory being named whenever a parent happens to carry a pom.
     */
    int doForget(StringBuilder out) {
        {
            if (!Files.exists(forget))
                out.append("# ").append(forget.toAbsolutePath()).append(" does not exist; looking it up anyway, "
                        + "since a project whose directory is gone is exactly one worth forgetting\n");
            var target = forget.toAbsolutePath().normalize();
            var row = Catalog.get().all().stream()
                    .filter(p -> p.root() != null && Path.of(p.root()).equals(target)).findFirst()
                    .orElseGet(() -> Catalog.get().project(Home.token(Projects.findRoot(forget))));
            if (row == null) {
                System.err.println("jacli: no indexed project at " + target + " - jacli projects lists what is indexed");
                return littlejlib.jacli.query.Answer.NOT_INDEXED;
            }
            var shared = littlejlib.jacli.index.ServerContext.index();
            if (shared != null) shared.release(row.projectId());
            Catalog.get().forget(row.projectId());
            out.append("# forgot ").append(row.coords()).append("  ").append(row.root()).append('\n')
                    .append("# ").append(row.files()).append(" files, ").append(row.declCount())
                    .append(" declarations and ").append(row.refCount()).append(" references dropped. ")
                    .append("Every other project is untouched.\n");
            common.emit(out.toString());
            return 0;
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
