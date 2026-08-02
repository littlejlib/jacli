package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.index.*;
import picocli.CommandLine.Command;

@Command(name = "projects", description = "Every project in the index, newest first.",
        footer = {
            "",
            "Coverage is reported per project because only what has been indexed is searchable, and an",
            "empty result on an unindexed project must not read as 'not found'."
        })
public final class ProjectsCmd implements Callable<Integer> {

    @Override
    public Integer call() {
        try {
            var rows = Counts.all(Db.get().db());
            var out = new StringBuilder("# " + rows.size() + " indexed projects\n");
            for (var p : rows) {
                out.append('\n').append(p.coords()).append(Boolean.TRUE.equals(p.watched()) ? "   [watched]" : "")
                        .append('\n');
                out.append("  ").append(p.root()).append('\n');
                out.append("  ").append(p.tier()).append("  ").append(p.files()).append(" files, ")
                        .append(p.declCount()).append(" declarations, ").append(p.refCount()).append(" references")
                        .append("  classpath=").append(p.classpathSource()).append('\n');
                out.append("  indexed ").append(ago(p.indexedAt())).append(" in ").append(p.indexMs()).append("ms\n");
                if (p.notes() != null && !p.notes().isBlank()) out.append("  ! ").append(p.notes()).append('\n');
            }
            if (rows.isEmpty()) out.append("# nothing indexed yet. Run any verb in a project, or jacli index.\n");
            System.out.print(out);
            return rows.isEmpty() ? 1 : 0;
        } finally {
            if (ServerContext.index() == null) Db.shutdown();
        }
    }

    static String ago(Long millis) {
        if (millis == null) return "never";
        var d = Duration.ofMillis(System.currentTimeMillis() - millis);
        if (d.toMinutes() < 1) return d.toSeconds() + "s ago";
        if (d.toHours() < 1) return d.toMinutes() + " min ago";
        return d.toDays() < 1 ? d.toHours() + "h ago" : d.toDays() + "d ago";
    }
}
