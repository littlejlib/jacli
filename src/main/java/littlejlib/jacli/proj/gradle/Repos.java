package littlejlib.jacli.proj.gradle;

import module java.base;

/**
 * The repositories a gradle build declares. This is what {@code releasable} has to read, because
 * gradle's artifact cache records no repository of origin — presence in the cache proves only that
 * one of these served it. Maven's {@code _remote.repositories} marker has no gradle equivalent, so
 * the question "could anyone else resolve this" is answered from the declarations instead.
 */
public final class Repos {

    private Repos() { }

    public static final Set<String> WELL_KNOWN = Set.of("mavenCentral", "gradlePluginPortal", "google");
    public static final String LOCAL = "mavenLocal", MAVEN = "maven";
    public static final Set<String> PUBLIC_HOSTS = Set.of("repo.maven.apache.org", "repo1.maven.org",
            "plugins.gradle.org", "maven.google.com", "dl.google.com", "jitpack.io",
            "oss.sonatype.org", "s01.oss.sonatype.org", "central.sonatype.com", "repo.gradle.org");

    static final Pattern URL = Pattern.compile("\\burl\\s*(?:=|\\()\\s*[^\"'\\n]*[\"']([^\"']+)[\"']");

    /**
     * Every declaration found, as a readable label. Order follows the files.
     * <p>
     * A url counts only inside a {@code maven { }} block. Matching {@code url =} anywhere reported
     * teavm's project homepage, its github page and the text of the Apache licence as repositories a
     * consumer would need configured — they are the {@code pom { }} metadata, and a release verdict
     * that names them is the kind of false alarm this whole verb exists not to raise.
     */
    public static List<String> declared(Path root, List<Path> moduleRoots) {
        var out = new LinkedHashSet<String>();
        for (var file : littlejlib.jacli.proj.Builds.buildFiles(root, moduleRoots)) {
            var src = Dsl.read(file);
            for (var name : WELL_KNOWN) if (!Dsl.occurrences(src, name).isEmpty()) out.add(name);
            if (!Dsl.occurrences(src, LOCAL).isEmpty()) out.add(LOCAL);
            for (var at : Dsl.occurrences(src, MAVEN)) {
                var m = URL.matcher(Dsl.block(src, at + MAVEN.length()));
                if (m.find()) out.add(m.group(1));
            }
        }
        return List.copyOf(out);
    }

    /** Declarations a consumer elsewhere could not resolve from without extra configuration. */
    public static List<String> nonPublic(List<String> declared) {
        return declared.stream().filter(d -> !WELL_KNOWN.contains(d)).filter(d -> !isPublicUrl(d)).toList();
    }

    public static boolean isPublicUrl(String value) {
        if (!value.startsWith("http")) return false;
        try {
            var host = URI.create(value).getHost();
            return host != null && PUBLIC_HOSTS.contains(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
