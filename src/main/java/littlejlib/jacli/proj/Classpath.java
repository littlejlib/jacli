package littlejlib.jacli.proj;

import module java.base;

/**
 * The compile classpath, cached against a pom fingerprint. This is the unglamorous part that
 * decides whether semantic resolution works at all, so it degrades in named steps and always
 * reports which one it used: cache, then offline maven, then online maven, then a direct walk of
 * the local m2 repository.
 * <p>
 * The project's own {@code target/classes} goes first. That is what makes a single-file reindex
 * cheap — one changed file resolves every project-internal type from the last build's bytecode
 * instead of recompiling its siblings.
 */
public final class Classpath {

    private Classpath() { }

    public static Resolved resolve(Path root, List<Path> moduleRoots, boolean force, Consumer<String> log) {
        var poms = poms(root, moduleRoots);
        var stamp = Fingerprint.digest(poms.stream().map(p -> Fingerprint.stamp(root, p.toString(), p)).toList());
        var token = Home.token(root);
        var cpFile = Home.file("cp", token + ".cp");
        var stampFile = Home.file("cp", token + ".stamp");
        var notes = new ArrayList<String>();

        var cached = readCache(cpFile, stampFile, stamp);
        if (!force && cached != null)
            return prepend(root, moduleRoots, cached.classpath(), "cache:" + cached.source(), notes);

        if (Mvn.buildClasspath(root, cpFile, true, notes::add))
            return cache(root, moduleRoots, read(cpFile), "maven-offline", stampFile, stamp, notes);
        log.accept("offline maven classpath failed, retrying online");
        if (Mvn.buildClasspath(root, cpFile, false, notes::add))
            return cache(root, moduleRoots, read(cpFile), "maven-online", stampFile, stamp, notes);

        /* Must not PREDICT an outcome. This note fires on every query for such a project, and claiming
         * results "will report as PARTIAL" when the tier above says RESOLVED teaches the caller to
         * distrust the tier — which is the one mechanism they are told to rely on. State the risk and
         * defer to the measured tier. */
        notes.add("maven could not produce a classpath; assembled it from the local m2 repository, "
                + "which can leave external types unresolved. If that happened, the tier above says "
                + "PARTIAL - a RESOLVED tier means it did not.");
        var jars = M2.classpathFor(poms, notes::add);
        var cp = jars.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        write(cpFile, cp);
        return cache(root, moduleRoots, cp, "m2-walk", stampFile, stamp, notes);
    }

    /**
     * Caches whatever was resolved, including the m2-walk fallback.
     * <p>
     * Only maven successes used to be cached, so a project maven cannot resolve paid two failed maven
     * process launches plus a pom walk on EVERY call — measured at 6-8 seconds per query once a scope
     * pulled such a project in. The expensive attempts have to happen once, not once per question.
     */
    static Resolved cache(Path root, List<Path> moduleRoots, String cp, String source,
                          Path stampFile, String stamp, List<String> notes) {
        write(stampFile, stamp + "\n" + source);
        return prepend(root, moduleRoots, cp, source, notes);
    }

    static Resolved prepend(Path root, List<Path> moduleRoots, String cp, String source, List<String> notes) {
        var parts = new ArrayList<String>();
        var missing = new ArrayList<String>();
        for (var dir : outputDirs(root, moduleRoots)) {
            if (Files.isDirectory(dir)) parts.add(dir.toString());
            else missing.add(littlejlib.jacli.core.Src.rel(root, dir));
        }
        var hasOwnClasses = !parts.isEmpty();
        if (cp != null && !cp.isBlank()) parts.add(cp.trim());
        if (!missing.isEmpty()) notes.add("no compiled output at " + String.join(", ", missing)
                + " - run mvn compile to make single-file reindex cheap and resolution more complete");
        return new Resolved(String.join(File.pathSeparator, parts), source, hasOwnClasses, List.copyOf(notes));
    }

    public static List<Path> outputDirs(Path root, List<Path> moduleRoots) {
        var out = new ArrayList<Path>();
        out.add(root.resolve("target").resolve("classes"));
        moduleRoots.forEach(m -> out.add(m.resolve("target").resolve("classes")));
        return out;
    }

    public static List<Path> poms(Path root, List<Path> moduleRoots) {
        var out = new ArrayList<Path>();
        var rootPom = root.resolve(Poms.POM);
        if (Files.isRegularFile(rootPom)) out.add(rootPom);
        for (var m : moduleRoots) {
            var p = m.resolve(Poms.POM);
            if (Files.isRegularFile(p)) out.add(p);
        }
        return out;
    }

    /** The cached classpath plus how it was originally obtained, or null when the poms have moved on. */
    static Cached readCache(Path cpFile, Path stampFile, String stamp) {
        if (!Files.isRegularFile(cpFile) || !Files.isRegularFile(stampFile)) return null;
        var saved = read(stampFile);
        if (saved == null) return null;
        var lines = saved.strip().lines().toList();
        if (lines.isEmpty() || !lines.getFirst().trim().equals(stamp)) return null;
        var cp = read(cpFile);
        return cp == null ? null : new Cached(cp, lines.size() > 1 ? lines.get(1).trim() : "maven");
    }

    static String read(Path p) {
        try {
            return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static void write(Path p, String s) {
        try {
            Files.writeString(p, s, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // a classpath cache that cannot be written only costs time on the next call
        }
    }
}
