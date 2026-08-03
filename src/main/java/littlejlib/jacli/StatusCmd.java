package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Command(name = "status", description = "What jacli sees for a project: sources, classpath, coverage, freshness.",
        footer = {
            "",
            "Run this first when an answer looks wrong. It names the resolution tier and where the",
            "classpath came from, which is what decides whether results are exact."
        })
public final class StatusCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Override
    public Integer call() {
        var project = Projects.open(common.where(), common.tests, false, common.log());
        var out = new StringBuilder();
        out.append("# project: ").append(project.coords()).append('\n');
        out.append("# root: ").append(project.root()).append('\n');
        out.append("# build: ").append(project.build().label()).append('\n');
        if (Builds.contested(project.root()))
            out.append("# ! this directory carries both a pom and a gradle build; ")
                    .append(project.build().label()).append(" was used. Pass --build ")
                    .append(project.build() == BuildKind.MAVEN ? "gradle" : "maven").append(" for the other\n");
        out.append("# java files: ").append(project.fileCount()).append('\n');
        project.sourceRoots().forEach(r -> out.append("# source root: ").append(r).append('\n'));
        project.moduleRoots().forEach(r -> out.append("# module: ").append(r).append('\n'));
        out.append("# classpath: ").append(project.classpath().entryCount()).append(" entries via ")
                .append(project.classpath().source()).append('\n');
        out.append("# compiled output on classpath: ").append(project.classpath().hasCompiledOutput()).append('\n');
        project.classpath().notes().forEach(n -> out.append("# ! ").append(n).append('\n'));
        System.out.print(out);
        return project.fileCount() == 0 ? 1 : 0;
    }
}
