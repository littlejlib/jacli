package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "releasable",
        description = "Could anyone else build this project? Names every blocker and what to do about it.",
        footer = {
            "",
            "  jacli releasable            verdict plus mitigations",
            "  jacli releasable -d 6       walk deeper before deciding",
            "",
            "The question this answers is why a project builds here and nowhere else: a dependency that",
            "only exists because somebody ran mvn install on this machine. Exit 0 publishable, 1 blocked."
        })
public final class ReleasableCmd implements Callable<Integer> {

    public static final int RELEASE_DEPTH = 6;

    @Mixin
    Common common;

    @Override
    public Integer call() {
        try {
            var root = Projects.findRoot(common.where());
            var coords = Poms.coords(root.resolve(Poms.POM));
            var all = DepGraph.of(root, Projects.moduleRoots(root), common.depthOr(RELEASE_DEPTH), common.log());
            var missing = all.stream().filter(n -> n.origin().isMissing()).toList();
            var localOnly = all.stream().filter(n -> n.origin().kind() == Origin.Kind.LOCAL_ONLY).toList();
            var otherRemote = all.stream()
                    .filter(n -> n.origin().kind() == Origin.Kind.REMOTE && !n.origin().isPublic()).toList();

            var out = new StringBuilder("# releasable: " + coords + "\n");
            out.append("# ").append(all.size()).append(" artifacts examined to depth ").append(common.depthOr(RELEASE_DEPTH)).append('\n');
            var blockers = missing.size() + localOnly.size();
            out.append(blockers == 0 && otherRemote.isEmpty()
                    ? "# PUBLISHABLE: every dependency resolves from maven central.\n"
                    : "# BLOCKED: " + blockers + " dependencies would not resolve for anyone else.\n");

            section(out, localOnly, "LOCAL-ONLY - installed on this machine, published nowhere",
                    "publish it to central, or fold its sources into this project, or shade it in");
            section(out, missing, "MISSING - not even in this machine's local repository",
                    "the build is broken here too: run mvn dependency:get, or correct the coordinates");
            section(out, otherRemote, "from a remote repository other than central",
                    "consumers need that repository configured; prefer central for a public release");

            if (blockers == 0 && otherRemote.isEmpty())
                out.append("# Nothing to mitigate. Note this reads poms only, so a profile-activated or\n")
                        .append("# dependencyManagement-supplied dependency could still be missed.\n");
            common.emit(out.toString());
            return blockers == 0 ? 0 : 1;
        } catch (RuntimeException e) {
            System.err.println("jacli: " + e.getMessage());
            return littlejlib.jacli.query.Answer.NOT_INDEXED;
        } finally {
            if (common.ownsDb()) littlejlib.jacli.index.Db.shutdown();
        }
    }

    static void section(StringBuilder out, List<DepNode> nodes, String title, String mitigation) {
        if (nodes.isEmpty()) return;
        out.append('\n').append(title).append('\n');
        nodes.stream().map(n -> n.coords().toString()).distinct().sorted()
                .forEach(c -> out.append("  ").append(c).append('\n'));
        out.append("  -> ").append(mitigation).append('\n');
    }
}
