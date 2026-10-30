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
            "only exists because somebody installed it on this machine. Exit 0 publishable, 1 blocked.",
            "",
            "On a gradle build the evidence differs. Gradle's cache records no repository of origin, so",
            "the verdict reads the repositories the build declares - mavenLocal(), or a private maven",
            "url, is the blocker there, and it is named."
        })
public final class ReleasableCmd implements Callable<Integer> {

    public static final int RELEASE_DEPTH = 6;

    @Mixin
    Common common;

    @Override
    public Integer call() {
        try {
            var root = Projects.findRoot(common.where());
            var modules = Projects.moduleRoots(root);
            var gradle = Builds.kindOf(root) == BuildKind.GRADLE;
            var depth = common.depthOr(RELEASE_DEPTH);
            var model = gradle
                    ? littlejlib.jacli.proj.gradle.GradleDeps.model(root, true, common.log()) : null;
            var all = model != null ? littlejlib.jacli.proj.gradle.GradleDeps.from(model, depth)
                    : DepGraph.of(root, modules, depth, common.log());
            var missing = all.stream().filter(n -> n.origin().isMissing()).toList();
            var localOnly = all.stream().filter(n -> n.origin().kind() == Origin.Kind.LOCAL_ONLY).toList();
            var otherRemote = all.stream()
                    .filter(n -> n.origin().kind() == Origin.Kind.REMOTE && !n.origin().isPublic()).toList();
            var repoFindings = gradle
                    ? littlejlib.jacli.proj.gradle.GradleDeps.repositoryFindings(root, modules) : List.<String>of();

            var out = new StringBuilder("# releasable: "
                    + (model != null ? model.coords() : Projects.coords(root))
                    + "  (" + Builds.kindOf(root).label() + ")\n");
            out.append("# ").append(all.size()).append(" artifacts examined to depth ").append(depth).append('\n');
            var blockers = missing.size() + localOnly.size();
            var clean = gradle
                    ? littlejlib.jacli.proj.gradle.GradleDeps.publishable(root, modules, all)
                    : blockers == 0 && otherRemote.isEmpty();
            out.append(clean
                    ? "# PUBLISHABLE: every dependency resolves from a repository anyone can reach.\n"
                    : "# BLOCKED: " + blockers + " dependencies would not resolve for anyone else.\n");
            repoFindings.forEach(f -> out.append("# ").append(f).append('\n'));

            section(out, localOnly, "LOCAL-ONLY - installed on this machine, published nowhere",
                    "publish it to central, or fold its sources into this project, or shade it in");
            section(out, missing, "MISSING - in neither the gradle cache nor the maven repository",
                    gradle ? "the build cannot resolve here either: run a gradle build to fetch them, or correct "
                            + "the coordinates" : "the build is broken here too: run mvn dependency:get, or "
                            + "correct the coordinates");
            section(out, otherRemote, "from a remote repository other than central",
                    "consumers need that repository configured; prefer central for a public release");

            if (clean) out.append(gradle
                    ? "# Nothing to mitigate. Gradle does not record which repository served a cached\n"
                            + "# artifact, so this verdict rests on the declared repositories above.\n"
                    : "# Nothing to mitigate. Note this reads poms only, so a profile-activated or\n"
                            + "# dependencyManagement-supplied dependency could still be missed.\n");
            common.emit(out.toString());
            return clean ? 0 : 1;
        } catch (RuntimeException e) {
            System.err.println("jacli: " + e.getMessage());
            return littlejlib.jacli.query.Answer.NOT_INDEXED;
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
