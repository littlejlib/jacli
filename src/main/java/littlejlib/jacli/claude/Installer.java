package littlejlib.jacli.claude;

import module java.base;

/**
 * Install, uninstall and status over one {@link Place}. Every write is skipped when the file already holds
 * exactly what would be written, which is what makes a second install change nothing at all. Uninstall
 * removes what install adds: the prompt file, the import line, and the hook entries carrying the marker.
 * A CLAUDE.md or settings.json left with nothing in it is deleted, since install may have created it.
 */
final class Installer {

    final Place p;
    final boolean dry;
    final List<String> log = new ArrayList<>();

    Installer(Place p, boolean dry) {
        this.p = p;
        this.dry = dry;
    }

    List<String> install() throws IOException {
        write(p.prompt(), Kit.prompt());
        write(p.claudeMd(), Memory.with(read(p.claudeMd()), p.importLine()));
        var root = Settings.read(p.settings());
        if (!Settings.current(root)) {
            Settings.strip(root);
            Settings.add(root);
            write(p.settings(), Settings.json(root));
        }
        return log;
    }

    List<String> uninstall() throws IOException {
        delete(p.prompt());
        var md = read(p.claudeMd());
        if (Memory.has(md, p.importLine())) {
            var rest = Memory.without(md, p.importLine());
            if (rest.isBlank()) delete(p.claudeMd());
            else write(p.claudeMd(), rest);
        }
        var root = Settings.read(p.settings());
        if (Settings.strip(root)) {
            if (root.isEmpty()) delete(p.settings());
            else write(p.settings(), Settings.json(root));
        }
        if (p.scope().equals("project")) deleteIfEmpty(p.root().resolve(".claude"));
        return log;
    }

    Status status() throws IOException {
        var lines = new ArrayList<String>();
        var prompt = !Files.exists(p.prompt()) ? "absent" : read(p.prompt()).equals(Kit.prompt()) ? "current" : "stale";
        lines.add(row("prompt", prompt, p.prompt().toString()));
        var imported = Memory.has(read(p.claudeMd()), p.importLine());
        lines.add(row("import", imported ? "present" : "absent", p.importLine() + " in " + p.claudeMd()));
        var root = Settings.read(p.settings());
        var ours = Settings.ours(root);
        var hooks = ours.isEmpty() ? "absent" : Settings.current(root) ? "current" : "stale";
        lines.add(row("hooks", hooks, ours.size() + " of " + Settings.expected().size() + " in " + p.settings()));
        var foreign = Settings.foreign(root);
        if (!foreign.isEmpty())
            lines.add("  note: " + foreign.size() + " other hook(s) in that file mention jacli and are not managed by install: "
                    + String.join(", ", foreign.stream().map(e -> e.substring(0, e.indexOf('|', e.indexOf('|') + 1))).toList()));
        var current = prompt.equals("current") && imported && hooks.equals("current");
        var absent = prompt.equals("absent") && !imported && hooks.equals("absent");
        return new Status(lines, current, absent);
    }

    static String row(String what, String state, String where) { return String.format("  %-8s %-8s %s", what, state, where); }

    static String read(Path f) throws IOException { return Files.exists(f) ? Files.readString(f) : ""; }

    void write(Path f, String content) throws IOException {
        if (Files.exists(f) && Files.readString(f).equals(content)) return;
        log.add((dry ? "would write  " : "wrote   ") + f);
        if (dry) return;
        Files.createDirectories(f.toAbsolutePath().getParent());
        Files.writeString(f, content);
    }

    void delete(Path f) throws IOException {
        if (!Files.exists(f)) return;
        log.add((dry ? "would delete " : "deleted ") + f);
        if (!dry) Files.delete(f);
    }

    void deleteIfEmpty(Path dir) throws IOException {
        if (dry || !Files.isDirectory(dir)) return;
        try (var s = Files.list(dir)) {
            if (s.findAny().isPresent()) return;
        }
        Files.delete(dir);
        log.add("deleted " + dir);
    }
}
