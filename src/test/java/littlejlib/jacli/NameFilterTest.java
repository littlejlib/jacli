package littlejlib.jacli;

import littlejlib.jacli.query.NameFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code --name} used to be a plain substring, so {@code --name 'Poster|Responder'} answered
 * "0 declarations - nothing named 'Poster|Responder' exists here". True as stated, and read by the
 * caller as this tool being weaker than the grep it is meant to replace — at the exact moment they
 * had been generous enough to try it on a real question. A text search accepts that pattern, so this
 * one has to.
 */
class NameFilterTest {

    @Test
    @DisplayName("a plain substring still matches the way it always did, case-insensitively")
    void substring() {
        var f = NameFilter.of("post");
        assertTrue(f.matches("postReply"));
        assertTrue(f.matches("ChatPoster"));
        assertTrue(f.matches("POSTING"));
        assertFalse(f.matches("reply"));
    }

    @Test
    @DisplayName("an alternation matches any branch - the shape a grep -E was reaching for")
    void alternation() {
        var f = NameFilter.of("post|reply|send");
        assertTrue(f.matches("postMessage"));
        assertTrue(f.matches("ReplyBuilder"));
        assertTrue(f.matches("sendNow"));
        assertFalse(f.matches("delete"));
    }

    @Test
    @DisplayName("a glob is anchored at both ends, so Gradle* is a prefix and not a substring")
    void glob() {
        var f = NameFilter.of("Gradle*");
        assertTrue(f.matches("GradleModel"));
        assertTrue(f.matches("Gradle"));
        assertFalse(f.matches("MyGradleThing"), "anchored: a leading segment must not match");
    }

    @Test
    @DisplayName("? is exactly one character")
    void singleCharGlob() {
        var f = NameFilter.of("Out_?");
        assertTrue(f.matches("Out_I"));
        assertTrue(f.matches("Out_A"));
        assertFalse(f.matches("Out_"));
        assertFalse(f.matches("Out_IX"));
    }

    @Test
    @DisplayName("a dot in a glob is literal, not a regex wildcard")
    void dotIsLiteralInAGlob() {
        var f = NameFilter.of("a.c*");
        assertTrue(f.matches("a.cde"));
        assertFalse(f.matches("abcde"), "the dot must not have matched 'b'");
    }

    @Test
    @DisplayName("~ opts into a real regex, for the caller who wants one")
    void regexAfterTilde() {
        var f = NameFilter.of("~^on[A-Z]");
        assertTrue(f.matches("onClick"));
        assertFalse(f.matches("nonClick"), "anchored by the caller's own ^");
    }

    @Test
    @DisplayName("a bare dot or paren is a substring, never a pattern - it is a qualified name far more often")
    void punctuationIsNotAccidentallyRegex() {
        assertTrue(NameFilter.of("luvml.E").matches("xluvml.Ey"));
        assertFalse(NameFilter.of("luvml.E").matches("luvmlXE"),
                "reading the dot as a regex wildcard would have matched this");
    }

    /**
     * A headless trial run wrote {@code --name "^px$"} expecting a regex, got it read as the literal
     * substring, found nothing, and retried with a looser pattern. An anchor is unambiguous intent and
     * no declaration is named with a leading ^ or a trailing $, so honouring it cannot cost a match.
     */
    @Test
    @DisplayName("^ and $ are honoured as anchors without needing the ~ prefix")
    void anchorsImplyRegex() {
        var exact = NameFilter.of("^px$");
        assertTrue(exact.matches("px"));
        assertTrue(exact.matches("PX"), "still case-insensitive");
        assertFalse(exact.matches("pxRem"));
        assertFalse(exact.matches("maxpx"));
        assertTrue(NameFilter.of("^get").matches("getName"));
        assertFalse(NameFilter.of("^get").matches("forgetName"));
    }

    @Test
    @DisplayName("no filter accepts everything, so call sites need no null check")
    void absentFilterAcceptsAll() {
        assertTrue(NameFilter.of(null).matches("anything"));
        assertTrue(NameFilter.of("").matches("anything"));
    }

    @Test
    @DisplayName("an empty branch in an alternation is dropped rather than matching everything")
    void emptyBranchDoesNotMatchAll() {
        var f = NameFilter.of("post||");
        assertTrue(f.matches("postThing"));
        assertFalse(f.matches("unrelated"), "a trailing | must not turn into match-all");
    }
}
