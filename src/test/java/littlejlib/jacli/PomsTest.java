package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.proj.Coords;
import littlejlib.jacli.proj.Poms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dependency reading, pinned because a naive version of it produced three false blockers in
 * {@code releasable}: an optional dependency, a pom-type dependency with no jar, and a version that
 * maven's nearest-wins mediation would never have chosen.
 */
class PomsTest {

    static Path pom() {
        var fromTarget = Path.of("target", "test-classes", "fixture-pom", "pom.xml");
        return Files.isRegularFile(fromTarget) ? fromTarget
                : Path.of("src", "test", "resources", "fixture-pom", "pom.xml");
    }

    static List<String> deps() { return Poms.directDependencies(pom()); }

    @Test
    @DisplayName("a property in a version is expanded")
    void expandsProperties() {
        assertTrue(deps().contains("keep:plain:9.9"), deps().toString());
    }

    @Test
    @DisplayName("optional, test and provided dependencies are not a consumer's problem")
    void excludesNonRequired() {
        var all = String.join(" ", deps());
        assertFalse(all.contains("optional-dep"), "optional must be excluded: " + all);
        assertFalse(all.contains("test-dep"), "test scope must be excluded: " + all);
        assertFalse(all.contains("provided-dep"), "provided scope must be excluded: " + all);
    }

    @Test
    @DisplayName("a pom-type dependency keeps its type, since it will never have a jar")
    void keepsPomType() {
        var bom = deps().stream().filter(d -> d.contains("bom")).findFirst().orElseThrow();
        assertTrue(Coords.parse(bom).isPomType(), bom);
    }

    @Test
    @DisplayName("an unexpandable version is reported as unresolved rather than guessed")
    void unexpandableVersionIsEmpty() {
        var dep = deps().stream().filter(d -> d.contains("unexpandable")).findFirst().orElseThrow();
        var v = Coords.parse(dep).version();
        assertTrue(v == null || v.isEmpty(), "version should be empty, was: " + dep);
    }

    @Test
    @DisplayName("coordinates round-trip with and without a type")
    void coordsParsing() {
        assertEquals("a", Coords.parse("a:b:1").groupId());
        assertEquals("1", Coords.parse("a:b:1").version());
        assertNull(Coords.parse("a:b:1").type());
        assertEquals("pom", Coords.parse("a:b:1:pom").type());
        assertTrue(Coords.parse("a:b:1:pom").isPomType());
    }
}
