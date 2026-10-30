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
        {
            var rows = Catalog.get().all();
            var out = new StringBuilder("# " + rows.size() + " indexed projects\n");
            var adHoc = 0;
            for (var p : rows) {
                var unknown = littlejlib.jacli.proj.Coords.isUnknown(p.coords());
                if (unknown) adHoc++;
                out.append('\n').append(p.coords()).append(Boolean.TRUE.equals(p.watched()) ? "   [watched]" : "")
                        .append(unknown ? "   [NO BUILD FILE - indexed as a plain directory of java files]" : "")
                        .append('\n');
                out.append("  ").append(p.root()).append('\n');
                out.append("  ").append(p.tier()).append("  ").append(p.files()).append(" files, ")
                        .append(p.declCount()).append(" declarations, ").append(p.refCount()).append(" references")
                        .append("  classpath=").append(p.classpathSource()).append('\n');
                out.append("  indexed ").append(ago(p.indexedAt())).append(" in ").append(p.indexMs()).append("ms\n");
                if (p.notes() != null && !p.notes().isBlank()) out.append("  ! ").append(p.notes()).append('\n');
                if (unknown) out.append("  forget it with:  jacli cache --forget ").append(p.root()).append('\n');
            }
            if (rows.isEmpty()) out.append("# nothing indexed yet. Run any verb in a project, or jacli index.\n");
            if (adHoc > 0) out.append("\n# ").append(adHoc)
                    .append(adHoc == 1 ? " project above has" : " projects above have").append(" no build file of their own.\n")
                    .append("# A bare source tree is legitimately this. So is the accident: a directory holding several\n")
                    .append("# sibling projects, indexed as one, so every java file beneath it became a single project.\n")
                    .append("# Check the file count against what you expect. jacli discover <dir> lists the real roots,\n")
                    .append("# and jacli cache --forget <dir> drops one entry without touching the others.\n");
            System.out.print(out);
            return rows.isEmpty() ? 1 : 0;
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
