package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.*;
import littlejlib.jacli.proj.gradle.*;
import littlejlib.jacli.proj.gradle.GradleRun;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A gradle build seen as a project: which directory is the root, what its sources are, who decides.
 * <p>
 * Gradle is never launched. These have to be offline and deterministic like the rest of the suite, and
 * the fixture has no wrapper, so an unguarded run would pick up whatever gradle happens to be on the
 * machine and answer differently on the next one.
 */
class GradleProjectTest {

    @BeforeAll
    static void doNotLaunchGradle() { System.setProperty(GradleRun.PROPERTY, "false"); }

    @AfterAll
    static void restore() { System.clearProperty(GradleRun.PROPERTY); }

    @AfterEach
    void clearPreference() { Builds.prefer(null); }

    static List<String> paths(List<Path> dirs) { return dirs.stream().map(GradleFixture::slashes).toList(); }

    @Test
    @DisplayName("a subproject resolves to the settings directory, not to itself")
    void findsTheBuildRoot() {
        var inside = GradleFixture.root().resolve("core").resolve("src").resolve("main").resolve("java");
        assertEquals(GradleFixture.root(), Projects.findRoot(inside));
        assertEquals(GradleFixture.root(), Projects.findRoot(GradleFixture.root().resolve("tools").resolve("cli")));
    }

    @Test
    @DisplayName("an included build resolves to the workspace that includes it")
    void climbsOutOfAnIncludedBuild() {
        assertEquals(GradleFixture.root(), Projects.findRoot(GradleFixture.root().resolve("build-logic")));
    }

    @Test
    @DisplayName("source roots cover the declared subprojects and the included build, and nothing else")
    void staticSourceRoots() {
        var model = StaticModel.of(GradleFixture.root(), false, s -> { });
        var dirs = paths(model.sourceRoots(false));
        assertTrue(dirs.stream().anyMatch(d -> d.endsWith("/core/src/main/java")), dirs.toString());
        assertTrue(dirs.stream().anyMatch(d -> d.endsWith("/tools/cli/src/main/java")), dirs.toString());
        assertTrue(dirs.stream().anyMatch(d -> d.endsWith("/build-logic/src/main/java")), dirs.toString());
        assertTrue(dirs.stream().noneMatch(d -> d.contains("/dead/")), dirs.toString());
    }

    @Test
    @DisplayName("coordinates come from gradle.properties and the settings file")
    void readsCoordinates() {
        assertEquals("org.example.fixture:fixture-gradle:1.2.3", Projects.coords(GradleFixture.root()).toString());
    }

    @Test
    @DisplayName("output directories are gradle's, not maven's")
    void outputDirsAreGradleShaped() {
        var dirs = paths(GradleProject.outputDirs(GradleFixture.root(),
                GradleProject.moduleRoots(GradleFixture.root())));
        assertTrue(dirs.stream().anyMatch(d -> d.endsWith("/core/build/classes/java/main")), dirs.toString());
        assertTrue(dirs.stream().noneMatch(d -> d.contains("/target/classes")), dirs.toString());
    }

    @Test
    @DisplayName("a settings file with computed includes falls back to finding build scripts on disk")
    void globFallback() {
        var notes = new ArrayList<String>();
        var dirs = StaticModel.discover(GradleFixture.dynamic(), notes);
        assertTrue(dirs.values().stream().anyMatch(p -> p.getFileName().toString().equals("alpha")),
                dirs.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("build scripts on disk")), notes.toString());
    }

    @Test
    @DisplayName("the model survives a round trip through its cache format")
    void modelRoundTrips() {
        var model = StaticModel.of(GradleFixture.root(), false, s -> { });
        var again = GradleModel.parse(GradleFixture.root(), model.render(), "test");
        assertEquals(model.coords().toString(), again.coords().toString());
        assertEquals(model.modules().size(), again.modules().size());
        assertEquals(model.sourceRoots(false), again.sourceRoots(false));
        assertEquals(model.moduleDirs(), again.moduleDirs());
    }

    @Test
    @DisplayName("maven wins a directory that carries both, and --build overrides it")
    void mavenWinsWhenContested(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(Poms.POM), "<project/>");
        Files.writeString(dir.resolve("build.gradle.kts"), "");
        assertTrue(Builds.contested(dir));
        assertEquals(BuildKind.MAVEN, Builds.kindOf(dir));
        Builds.prefer(BuildKind.GRADLE);
        assertEquals(BuildKind.GRADLE, Builds.kindOf(dir), "--build gradle must win");
        Builds.prefer(BuildKind.MAVEN);
        assertEquals(BuildKind.MAVEN, Builds.kindOf(dir));
    }

    @Test
    @DisplayName("a preference for a build system the directory does not have is ignored")
    void preferenceCannotInventABuild(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(Poms.POM), "<project/>");
        Builds.prefer(BuildKind.GRADLE);
        assertEquals(BuildKind.MAVEN, Builds.kindOf(dir));
    }

    @Test
    @DisplayName("a gradle directory is a project root, so a workspace scan stops there")
    void gradleDirectoryIsAProjectRoot() {
        assertTrue(Workspace.isProjectRoot(GradleFixture.root()));
        assertFalse(Workspace.isWorkspace(GradleFixture.root()));
        assertTrue(Workspace.markers().contains("settings.gradle.kts"), Workspace.markers().toString());
    }

    @Test
    @DisplayName("the build system is recorded on the project, since it changes how answers were obtained")
    void projectNamesItsBuild() {
        var project = Projects.open(GradleFixture.root().resolve("core"), false, false, s -> { });
        assertEquals(BuildKind.GRADLE, project.build());
        assertTrue(project.fileCount() >= 3, "core, cli and build-logic sources: " + project.fileCount());
        assertTrue(project.describe().contains("gradle"), project.describe());
    }
}
