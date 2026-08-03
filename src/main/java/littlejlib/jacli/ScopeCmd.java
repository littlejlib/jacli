package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.index.*;
import littlejlib.jacli.proj.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import xyz.jphil.arcadedb.datahelper.Upsert;

@Command(name = "scope", description = "Declare that several projects belong together, so one query can span them.",
        footer = {
            "",
            "For a family of sibling maven projects with no root pom, where the type system lives in one",
            "and the implementations in another. Verbs stay single-project by default; --scope widens them.",
            "",
            "  jacli scope                              every declared scope and its members",
            "  jacli scope luvml --add . --add ../luvx-base --add ../luvs   (forward slashes even on Windows)",
            "  jacli scope luvml --add .                add the current project to that scope",
            "  jacli scope luvml --remove .             take it out",
            "  jacli scope --suggest                    propose scopes from local-only dependencies",
            "",
            "Then:  jacli impls luvx.Frag_I --scope luvml",
            "       jacli unused --scope luvml          finally meaningful for a library",
            "",
            "Or widen one call without declaring anything:  jacli refs X --with ../luvx-base"
        })
public final class ScopeCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", arity = "0..1", paramLabel = "<name>", description = "The scope to inspect or edit.")
    String name;

    @Option(names = "--add", paramLabel = "<dir>", description = "Add this project to the scope. Repeatable.")
    List<Path> add = new ArrayList<>();

    @Option(names = "--remove", paramLabel = "<dir>", description = "Remove this project from the scope. Repeatable.")
    List<Path> remove = new ArrayList<>();

    @Option(names = "--suggest",
            description = "Propose scopes: a local-only dependency that is also an indexed project is a sibling.")
    boolean suggest;

    @Override
    public Integer call() {
        try {
            var db = Db.get().db();
            if (suggest) return suggest(db);
            if (name == null) return list(db);
            add.forEach(dir -> set(db, dir, name));
            remove.forEach(dir -> set(db, dir, null));
            return show(db, name);
        } catch (IllegalArgumentException e) {
            System.err.println("jacli: " + e.getMessage());
            return littlejlib.jacli.query.Answer.USAGE;
        } catch (RuntimeException e) {
            System.err.println("jacli: " + e.getMessage());
            return littlejlib.jacli.query.Answer.NOT_INDEXED;
        } finally {
            if (ServerContext.index() == null) Db.shutdown();
        }
    }

    int list(com.arcadedb.database.Database db) {
        var groups = Counts.groups(db);
        var out = new StringBuilder("# " + littlejlib.jacli.query.Fmt.plural(groups.size(), "declared scope", "declared scopes") + "\n");
        for (var g : groups) {
            out.append('\n').append(g).append('\n');
            Counts.inGroup(db, g).forEach(p -> out.append("  ").append(p.coords())
                    .append("   ").append(p.root()).append('\n'));
        }
        if (groups.isEmpty()) out.append("# none. jacli scope --suggest proposes some, "
                + "or jacli scope <name> --add <dir> declares one.\n");
        common.emit(out.toString());
        return groups.isEmpty() ? 1 : 0;
    }

    int show(com.arcadedb.database.Database db, String group) {
        var members = Counts.inGroup(db, group);
        var out = new StringBuilder("# scope " + group + ": "
                + littlejlib.jacli.query.Fmt.plural(members.size(), "project", "projects") + "\n");
        members.forEach(p -> out.append("  ").append(p.coords()).append("   ").append(p.root()).append('\n'));
        if (members.isEmpty()) out.append("# empty. Add projects with --add <dir>.\n");
        else out.append("# use it:  jacli <verb> <symbol> --scope ").append(group).append('\n');
        common.emit(out.toString());
        return members.isEmpty() ? 1 : 0;
    }

    /**
     * A path that does not exist is a different answer from one that exists but has not been indexed.
     * Reporting the second for the first tells the reader to run a verb in a directory they cannot cd
     * into, and hides the real cause, which on Windows is usually a backslash eaten by the shell.
     */
    void set(com.arcadedb.database.Database db, Path dir, String group) {
        if (group != null && !Files.exists(dir))
            throw new IllegalArgumentException("no such path: " + dir.toAbsolutePath()
                    + " - that is how it resolved, so compare it with what you typed");
        var root = Projects.findRoot(dir);
        var pid = Home.token(root);
        if (Counts.project(db, pid) == null)
            throw new IllegalStateException(root + " is not indexed yet - run a verb in it, or jacli index -p " + root);
        db.transaction(() -> Upsert.upsert(db, JProject.TYPEDEF)
                .key(JProject.$projectId, pid).set(JProject.$scopeGroup, group).save());
    }

    /**
     * A dependency that exists only because somebody ran {@code mvn install}, and which is ALSO an
     * indexed project here, is a sibling by definition. {@code deps} and {@code projects} already know
     * both halves, so the proposal is free.
     */
    int suggest(com.arcadedb.database.Database db) {
        var indexed = Counts.all(db);
        var byCoords = new HashMap<String, JProject>();
        indexed.forEach(p -> {
            if (p.coords() != null) byCoords.put(coordKey(p.coords()), p);
        });
        var out = new StringBuilder("# scope suggestions, from local-only dependencies that are also indexed here\n");
        var found = 0;
        for (var p : indexed) {
            if (p.root() == null) continue;
            var root = Path.of(p.root());
            if (!Files.isDirectory(root)) continue;
            var siblings = DepGraph.of(root, Projects.moduleRoots(root), 1, s -> { }).stream()
                    .filter(n -> n.origin().kind() == Origin.Kind.LOCAL_ONLY)
                    .map(n -> byCoords.get(coordKey(n.coords().toString())))
                    .filter(Objects::nonNull).map(JProject::root).distinct().toList();
            if (siblings.isEmpty()) continue;
            found++;
            out.append('\n').append(p.coords()).append(" depends on indexed siblings:\n");
            siblings.forEach(s -> out.append("  ").append(s).append('\n'));
            out.append("  jacli scope <name> --add ").append(p.root());
            siblings.forEach(s -> out.append(" --add ").append(s));
            out.append('\n');
        }
        if (found == 0) out.append("# none found among the ").append(indexed.size())
                .append(" indexed projects. Index the siblings first.\n");
        common.emit(out.toString());
        return found == 0 ? 1 : 0;
    }

    static String coordKey(String coords) {
        var p = coords.split(":");
        return p.length >= 2 ? p[0] + ":" + p[1] : coords;
    }
}
