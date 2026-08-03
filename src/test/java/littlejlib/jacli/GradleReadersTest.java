package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.Builds;
import littlejlib.jacli.proj.gradle.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The textual readers, which are what jacli falls back to when gradle itself cannot be run. Each of
 * these pins a way a naive reader gets a real build wrong: a commented-out include, a coordinate that
 * only exists in the version catalog, a test dependency reported as a release blocker.
 */
class GradleReadersTest {

    static List<String> keys(List<littlejlib.jacli.proj.Coords> coords) {
        return coords.stream().map(c -> c.groupId() + ":" + c.artifactId()).toList();
    }

    static List<Path> buildFiles() {
        return Builds.buildFiles(GradleFixture.root(), GradleProject.moduleRoots(GradleFixture.root()));
    }

    @Test
    @DisplayName("include() is read literally, and a commented-out one is not a module")
    void readsIncludes() {
        var projects = Settings.projects(GradleFixture.root());
        assertEquals(Set.of(":core", ":tools:cli"), projects.keySet(), projects.toString());
        assertTrue(GradleFixture.slashes(projects.get(":tools:cli")).endsWith("/tools/cli"),
                projects.get(":tools:cli").toString());
    }

    @Test
    @DisplayName("a directory the build abandoned contributes no module and no dependency")
    void deadDirectoryIsExcluded() {
        var modules = GradleProject.moduleRoots(GradleFixture.root());
        assertTrue(modules.stream().noneMatch(p -> p.getFileName().toString().equals("dead")), modules.toString());
        assertFalse(keys(Deps.mentioned(buildFiles(), Catalog.of(GradleFixture.root())))
                .contains("org.example:never-wanted"));
    }

    @Test
    @DisplayName("includeBuild is followed, so build logic is part of the same workspace")
    void findsIncludedBuilds() {
        var builds = Settings.includedBuilds(GradleFixture.root());
        assertTrue(builds.stream().anyMatch(p -> p.getFileName().toString().equals("build-logic")),
                builds.toString());
    }

    @Test
    @DisplayName("every version catalog spelling resolves to coordinates")
    void readsVersionCatalog() {
        var catalog = Catalog.of(GradleFixture.root());
        assertEquals("org.ow2.asm:asm-commons:9.8", String.valueOf(catalog.lookup("asm.commons")));
        assertEquals("com.carrotsearch:hppc:0.10.0", String.valueOf(catalog.lookup("hppc")));
        assertEquals("org.example:grouped:2.0", String.valueOf(catalog.lookup("grouped")));
        assertEquals("org.example:split:9.8", String.valueOf(catalog.lookup("split.table")));
    }

    @Test
    @DisplayName("declared dependencies exclude test configurations; mentioned ones do not")
    void declaredVersusMentioned() {
        var catalog = Catalog.of(GradleFixture.root());
        var declared = keys(Deps.declared(buildFiles(), catalog, false));
        assertTrue(declared.contains("org.ow2.asm:asm-commons"), declared.toString());
        assertTrue(declared.contains("com.carrotsearch:hppc"), declared.toString());
        assertTrue(declared.contains("org.example:grouped"), declared.toString());
        assertTrue(declared.contains("org.example:split"), declared.toString());
        assertFalse(declared.contains("junit:junit"), "a test dependency is not a release blocker");
        assertTrue(keys(Deps.mentioned(buildFiles(), catalog)).contains("junit:junit"),
                "the classpath reading is deliberately over-inclusive");
    }

    @Test
    @DisplayName("project(\":core\") is recognised as a sibling rather than as coordinates")
    void readsProjectRefs() {
        assertTrue(Deps.projectRefs(buildFiles()).contains(":core"));
    }

    @Test
    @DisplayName("comments go, string literals stay")
    void stripsComments() {
        var stripped = Dsl.strip("val a = \"https://x/y\" // note\n/* block */ val b = 1");
        assertTrue(stripped.contains("https://x/y"), stripped);
        assertFalse(stripped.contains("note"), stripped);
        assertFalse(stripped.contains("block"), stripped);
        assertTrue(stripped.contains("val b = 1"), stripped);
    }

    @Test
    @DisplayName("call arguments are read in kotlin, groovy and multi-line forms")
    void readsCallArguments() {
        var n = "include".length();
        assertEquals(List.of("a", "b"), Dsl.args("include(\"a\", \"b\")", n));
        assertEquals(List.of("a", "b"), Dsl.args("include 'a', 'b'\ninclude 'c'", n));
        assertEquals(List.of("x"), Dsl.args("include(\n  \"x\",\n)", n));
        assertTrue(Dsl.args("include(projects.core)", n).isEmpty(),
                "a computed include must yield nothing rather than something invented");
    }

    @Test
    @DisplayName("declared repositories are found, and the private ones are named")
    void readsRepositories() {
        var declared = Repos.declared(GradleFixture.root(), GradleProject.moduleRoots(GradleFixture.root()));
        assertTrue(declared.contains("mavenCentral"), declared.toString());
        assertTrue(declared.contains("mavenLocal"), declared.toString());
        assertTrue(declared.contains("https://nexus.internal.example.com/repo"), declared.toString());
        assertFalse(declared.contains("https://never.example.com/commented-out"), "commented out");
        var nonPublic = Repos.nonPublic(declared);
        assertTrue(nonPublic.contains("mavenLocal"), nonPublic.toString());
        assertTrue(nonPublic.contains("https://nexus.internal.example.com/repo"), nonPublic.toString());
        assertFalse(nonPublic.contains("mavenCentral"), nonPublic.toString());
    }

    @Test
    @DisplayName("a url in pom metadata is not a repository")
    void pomMetadataIsNotARepository() {
        var declared = Repos.declared(GradleFixture.root(), GradleProject.moduleRoots(GradleFixture.root()));
        assertFalse(declared.contains("https://not-a-repository.example.org"), declared.toString());
        assertFalse(declared.contains("http://www.apache.org/licenses/LICENSE-2.0.txt"), declared.toString());
        assertFalse(declared.contains("https://github.com/example/fixture-gradle"), declared.toString());
        assertTrue(declared.contains("https://nexus.internal.example.com/repo"),
                "the one inside maven { } must still be found: " + declared);
    }

    @Test
    @DisplayName("a public repository url is not reported as a consumer's problem")
    void knowsPublicHosts() {
        assertTrue(Repos.isPublicUrl("https://repo1.maven.org/maven2"));
        assertFalse(Repos.isPublicUrl("https://nexus.internal.example.com/repo"));
        assertFalse(Repos.isPublicUrl("mavenCentral"));
    }
}
