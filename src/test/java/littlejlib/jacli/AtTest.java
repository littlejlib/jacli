package littlejlib.jacli;

import littlejlib.jacli.query.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code at} exists because every other verb starts from a symbol and a caller starts from a
 * position — a grep hit, a stack frame, a compiler error, a line in a diff. The observed workaround
 * was {@code grep -n} followed by {@code sed -n 'A,Bp'}, reading upwards through a file to work out
 * which method a line was in: precisely the manual step this tool exists to remove.
 */
class AtTest {

    static final Opts OPTS = Opts.defaults();

    @Test
    @DisplayName("a file and a line name the innermost declaration spanning it")
    void innermostDeclaration() {
        var answer = At.at(Fixture.store(), "fx/Circle.java:15", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("area"), answer.text());
    }

    @Test
    @DisplayName("enclosing declarations are listed outwards, so the owning type is there too")
    void enclosingChain() {
        var answer = At.at(Fixture.store(), "fx/Circle.java:15", OPTS);
        assertTrue(answer.text().contains("fx.Circle"), answer.text());
    }

    @Test
    @DisplayName("a bare file name is enough - the path is matched by suffix")
    void bareFileNameResolves() {
        var answer = At.at(Fixture.store(), "Circle.java:15", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("area"), answer.text());
    }

    @Test
    @DisplayName("with no line it lists what the file declares")
    void wholeFile() {
        var answer = At.at(Fixture.store(), "Circle.java", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("area"), answer.text());
        assertTrue(answer.text().contains("scaled"), answer.text());
    }

    @Test
    @DisplayName("symbols used on that line are reported with the key each resolved to")
    void referencedSymbolsAreResolved() {
        var answer = At.at(Fixture.store(), "fx/Shapes.java:18", OPTS);
        assertEquals(Answer.OK, answer.exit(), answer.text());
        assertTrue(answer.text().contains("referenced on and around"), answer.text());
        assertTrue(answer.text().contains("fx.Shape"), "the resolved key belongs in the answer: " + answer.text());
    }

    @Test
    @DisplayName("the follow-on commands are printed, since a spec nobody can paste is no use")
    void nextStepsArePrinted() {
        var answer = At.at(Fixture.store(), "fx/Circle.java:15", OPTS);
        assertTrue(answer.text().contains("jacli src "), answer.text());
        assertTrue(answer.text().contains("jacli callers "), answer.text());
    }

    @Test
    @DisplayName("an unknown file says so rather than pretending the line is empty")
    void unknownFile() {
        var answer = At.at(Fixture.store(), "NoSuchFile.java:3", OPTS);
        assertEquals(Answer.NOTHING_FOUND, answer.exit(), answer.text());
        assertTrue(answer.text().contains("no indexed java file"), answer.text());
    }

    @Test
    @DisplayName("a windows path is split from the right, so C: is not taken for a filename")
    void windowsDriveLetterSurvives() {
        var loc = At.Loc.parse("C:\\user\\code\\fx\\Circle.java:15");
        assertNotNull(loc);
        assertEquals(15, loc.line());
        assertTrue(loc.file().endsWith("fx/Circle.java"), loc.file());
    }

    @Test
    @DisplayName("a trailing column is accepted and the LINE is what is kept")
    void lineAndColumn() {
        var loc = At.Loc.parse("fx/Circle.java:15:9");
        assertNotNull(loc);
        assertEquals(15, loc.line());
        assertEquals("fx/Circle.java", loc.file());
    }

    @Test
    @DisplayName("no line at all parses as the whole file rather than as a failure")
    void noLine() {
        var loc = At.Loc.parse("fx/Circle.java");
        assertNotNull(loc);
        assertEquals(0, loc.line());
    }

    @Test
    @DisplayName("empty input is a usage error, not an exception")
    void emptyInput() {
        assertEquals(Answer.USAGE, At.at(Fixture.store(), "  ", OPTS).exit());
    }
}
