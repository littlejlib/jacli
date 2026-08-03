package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pointing a verb at a directory that HOLDS projects used to index every java file beneath it as one
 * nameless project — 1753 files from five unrelated trees, answering as though they compiled together.
 * <p>
 * The risk in refusing it is over-refusing, so {@link #bareSourceTreeIsNotRefused} matters as much as
 * the refusal itself: a directory with no build file anywhere is a real, if unnamed, project and has to
 * keep working.
 */
class WorkspaceTest {

    static void pom(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pom.xml"),
                "<project><groupId>g</groupId><artifactId>" + dir.getFileName() + "</artifactId>"
                        + "<version>1</version></project>");
    }

    static void source(Path dir) throws IOException {
        var pkg = dir.resolve("src").resolve("main").resolve("java").resolve("x");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("A.java"), "package x; public class A { }");
    }

    @Test
    @DisplayName("a directory holding sibling projects is a workspace, and is refused by name")
    void workspaceOfSiblingsIsRefused(@TempDir Path ws) throws IOException {
        pom(ws.resolve("alpha"));
        pom(ws.resolve("beta"));
        assertTrue(Workspace.isWorkspace(ws));
        assertEquals(List.of(ws.resolve("alpha"), ws.resolve("beta")), Workspace.roots(ws, 4));

        var e = assertThrows(IllegalStateException.class, () -> Workspace.refuseIfWorkspace(ws));
        assertTrue(e.getMessage().contains("alpha"), e.getMessage());
        assertTrue(e.getMessage().contains("beta"), e.getMessage());
        assertTrue(e.getMessage().contains("--add"), "the remedy must be runnable: " + e.getMessage());
    }

    @Test
    @DisplayName("a bare source tree with no build file is NOT refused - it is a real unnamed project")
    void bareSourceTreeIsNotRefused(@TempDir Path plain) throws IOException {
        source(plain);
        assertFalse(Workspace.isWorkspace(plain));
        assertDoesNotThrow(() -> Workspace.refuseIfWorkspace(plain));
    }

    @Test
    @DisplayName("a directory that is itself a project is not a workspace, whatever it contains")
    void projectRootIsNotAWorkspace(@TempDir Path root) throws IOException {
        pom(root);
        pom(root.resolve("child"));
        assertFalse(Workspace.isWorkspace(root));
        assertDoesNotThrow(() -> Workspace.refuseIfWorkspace(root));
    }

    @Test
    @DisplayName("the walk stops at a project root - a reactor's modules are indexed with it, not separately")
    void rootsDoNotDescendIntoAFoundRoot(@TempDir Path ws) throws IOException {
        pom(ws.resolve("alpha"));
        pom(ws.resolve("alpha").resolve("module-one"));
        assertEquals(List.of(ws.resolve("alpha")), Workspace.roots(ws, 4));
    }

    @Test
    @DisplayName("build output and VCS directories are never walked into")
    void buildOutputIsSkipped(@TempDir Path ws) throws IOException {
        pom(ws.resolve("target").resolve("unpacked"));
        pom(ws.resolve(".git").resolve("modules").resolve("x"));
        assertEquals(List.of(), Workspace.roots(ws, 4));
        assertFalse(Workspace.isWorkspace(ws), "a pom under target/ must not make this a workspace");
    }

    @Test
    @DisplayName("depth bounds the walk")
    void depthIsHonoured(@TempDir Path ws) throws IOException {
        pom(ws.resolve("a").resolve("b").resolve("c"));
        assertEquals(List.of(), Workspace.roots(ws, 1));
        assertEquals(List.of(ws.resolve("a").resolve("b").resolve("c")), Workspace.roots(ws, 3));
    }

    @Test
    @DisplayName("an empty directory is neither a workspace nor a refusal")
    void emptyDirectoryIsNotAWorkspace(@TempDir Path empty) {
        assertFalse(Workspace.isWorkspace(empty));
        assertDoesNotThrow(() -> Workspace.refuseIfWorkspace(empty));
    }
}
