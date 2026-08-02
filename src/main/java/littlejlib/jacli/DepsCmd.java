package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "deps", description = "The dependency tree, and where each artifact actually came from.",
        footer = {
            "",
            "  jacli deps                every dependency, 3 levels deep, with its origin",
            "  jacli deps -d 1           direct dependencies only",
            "  jacli deps --dependents io.github.luvml:luvs",
            "                            which of your indexed projects depend on that artifact",
            "",
            "Origins are read offline from the local repository's resolver markers:",
            "  central       downloaded from maven central - anyone can build against it",
            "  remote:<id>   from some other remote repository",
            "  LOCAL-ONLY    somebody ran mvn install here; nobody else has it",
            "  MISSING       not in the local repository at all",
            "",
            "Poms only, no compilation. Profiles, dependencyManagement and version ranges are not",
            "expanded, and unresolvable versions are reported rather than guessed."
        })
public final class DepsCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Option(names = "--dependents", paramLabel = "<groupId:artifactId>",
            description = "Instead of the tree, list indexed projects that depend on this artifact.")
    String dependents;

    @Override
    public Integer call() {
        try {
            if (dependents != null) return listDependents();
            var root = Projects.findRoot(common.where());
            var modules = Projects.moduleRoots(root);
            var roots = DepGraph.of(root, modules, common.depthOr(DepGraph.DEFAULT_DEPTH), common.log());
            var all = roots;
            var out = new StringBuilder("# deps of " + Poms.coords(root.resolve(Poms.POM)) + "\n");
            out.append("# ").append(all.size()).append(" artifacts to depth ").append(common.depthOr(DepGraph.DEFAULT_DEPTH)).append('\n');
            out.append(DepGraph.render(roots));
            common.emit(out.toString());
            return all.isEmpty() ? 1 : 0;
        } catch (RuntimeException e) {
            System.err.println("jacli: " + e.getMessage());
            return littlejlib.jacli.query.Answer.NOT_INDEXED;
        } finally {
            if (common.ownsDb()) littlejlib.jacli.index.Db.shutdown();
        }
    }

    int listDependents() {
        var roots = littlejlib.jacli.index.Counts.all(littlejlib.jacli.index.Db.get().db()).stream()
                .map(littlejlib.jacli.index.JProject::root).filter(Objects::nonNull).map(Path::of).toList();
        var hits = DepGraph.dependentsAmong(roots, dependents);
        var out = new StringBuilder("# projects depending on " + dependents + "\n");
        out.append("# searched ").append(roots.size()).append(" indexed projects - only what is indexed is searched, ")
                .append("so an empty result is not proof that nothing depends on it\n");
        hits.forEach(h -> out.append("  ").append(h).append('\n'));
        if (hits.isEmpty()) out.append("# none among the indexed projects\n");
        common.emit(out.toString());
        return hits.isEmpty() ? 1 : 0;
    }
}
