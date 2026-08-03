package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.index.*;
import littlejlib.jacli.proj.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "discover", description = "Find the project roots under a directory, without indexing anything.",
        footer = {
            "",
            "The one question the index cannot answer, because it only knows what has already been",
            "indexed. scope --suggest proposes siblings it has SEEN; this finds the ones it has not.",
            "",
            "  jacli discover                    project roots under the working directory",
            "  jacli discover C:/user/code       under somewhere else",
            "  jacli discover -d 2               how far down to look (default 4)",
            "  jacli discover --as-scope luvml   print the scope command that declares them together",
            "",
            "Nothing is read but directory names and the presence of a build file, so this costs no",
            "compilation and no classpath resolution. A reactor is reported once, at its root: its",
            "modules are indexed with it, not separately."
        })
public final class DiscoverCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<dir>",
            description = "Where to look. Defaults to the working directory.")
    Path dir;

    @Option(names = "--as-scope", paramLabel = "<name>",
            description = "Also print the ready-to-run scope command that declares everything found as one family.")
    String scopeName;

    @Override
    public Integer call() {
        var where = dir != null ? dir : common.where();
        var depth = common.depthOr(Workspace.MAX_DEPTH);
        if (!Files.isDirectory(where)) {
            System.err.println("jacli: not a directory: " + where.toAbsolutePath());
            return littlejlib.jacli.query.Answer.USAGE;
        }
        var roots = Workspace.roots(where, depth);
        var out = new StringBuilder("# " + roots.size() + (roots.size() == 1 ? " project root" : " project roots")
                + " under " + where.toAbsolutePath() + "  (depth " + depth + ", nothing indexed)\n");
        if (roots.isEmpty()) return none(where, out, depth);

        var indexed = indexedRoots();
        for (var r : roots) {
            out.append('\n').append(r).append(indexed.contains(r) ? "   [indexed]" : "   [not indexed]").append('\n');
            var kind = Builds.kindOf(r);
            out.append("  ").append(kind.label()).append("   ").append(Projects.coords(r))
                    .append(Builds.contested(r) ? "   (both a pom and a gradle build are present)" : "").append('\n');
            var modules = Projects.moduleRoots(r);
            if (!modules.isEmpty()) out.append("  ").append(modules.size())
                    .append(modules.size() == 1 ? " module, indexed with it: " : " modules, indexed with it: ")
                    .append(modules.stream().map(m -> r.relativize(m).toString()).collect(Collectors.joining(", ")))
                    .append('\n');
        }
        var fresh = roots.stream().filter(r -> !indexed.contains(r)).toList();
        if (!fresh.isEmpty()) out.append("\n# index the ones you need:  ")
                .append(fresh.stream().map(r -> "jacli index -p " + r).collect(Collectors.joining("  ;  ")))
                .append('\n');
        if (roots.size() > 1) out.append("# query them as one family:  jacli scope ")
                .append(scopeName == null ? "<name>" : scopeName)
                .append(roots.stream().map(r -> " --add " + r).collect(Collectors.joining())).append('\n');
        common.emit(out.toString());
        return 0;
    }

    int none(Path where, StringBuilder out, int depth) {
        out.append("# no build file (").append(String.join(", ", Workspace.markers()))
                .append(") found at or under depth ").append(depth).append(".\n");
        if (Workspace.hasSourceLayout(where))
            out.append("# it does have src/main/java, so it is usable as an unnamed project on its own.\n");
        else out.append("# try a larger --depth, or check the path.\n");
        common.emit(out.toString());
        return littlejlib.jacli.query.Answer.NOTHING_FOUND;
    }

    /** Absolute roots already in the index, so each hit can say whether it is new. */
    Set<Path> indexedRoots() {
        try {
            return Counts.all(Db.get().db()).stream().map(JProject::root).filter(Objects::nonNull)
                    .map(Path::of).collect(Collectors.toSet());
        } catch (RuntimeException e) {
            return Set.of();
        } finally {
            if (ServerContext.index() == null) Db.shutdown();
        }
    }
}
