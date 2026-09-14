package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * One call, several symbols — and the banner folding that makes it worth doing.
 * <p>
 * Asking one symbol at a time was the single structural reason a grep alternation beat this tool on
 * the commonest real question. Answering six symbols in six calls costs six round trips and six
 * copies of a project-wide banner that says the same thing every time; the batch has to be cheaper
 * than the grep it replaces, not merely more accurate than it.
 */
class MultiSymbolTest {

    static final Opts OPTS = Opts.defaults();

    @Test
    @DisplayName("the shared context line is printed once, however many symbols follow")
    void contextIsPrintedOnce() {
        Fmt.beginAnswer();
        var first = Verbs.def(Fixture.store(), "fx.Circle", OPTS).text();
        var second = Verbs.def(Fixture.store(), "fx.Shapes", OPTS).text();
        var tier = Fixture.store().meta().line();
        assertTrue(first.contains(tier), "the first answer must carry the full context: " + first);
        assertFalse(second.contains(tier), "the second must not repeat it: " + second);
    }

    @Test
    @DisplayName("every answer still names its own symbol, so a folded banner cannot make one anonymous")
    void eachAnswerStillIdentifiesItself() {
        Fmt.beginAnswer();
        Verbs.def(Fixture.store(), "fx.Circle", OPTS);
        var second = Verbs.def(Fixture.store(), "fx.Shapes", OPTS).text();
        assertTrue(second.contains("fx.Shapes"), second);
        assertTrue(second.startsWith("# fx.Shapes"), second);
    }

    @Test
    @DisplayName("beginAnswer resets the guard, or a pooled daemon thread would silence the next request")
    void beginAnswerResetsTheGuard() {
        Fmt.beginAnswer();
        var one = Verbs.def(Fixture.store(), "fx.Circle", OPTS).text();
        Fmt.beginAnswer();
        var two = Verbs.def(Fixture.store(), "fx.Circle", OPTS).text();
        assertEquals(one, two, "a fresh request must render identically to the first one");
        assertTrue(two.contains(Fixture.store().meta().line()), two);
    }

    @Test
    @DisplayName("a symbol that does not exist folds the banner too, rather than re-dumping it")
    void unknownSymbolAlsoFolds() {
        Fmt.beginAnswer();
        Verbs.def(Fixture.store(), "fx.Circle", OPTS);
        var missing = Verbs.def(Fixture.store(), "fx.NoSuchTypeHere", OPTS);
        assertEquals(Answer.NOTHING_FOUND, missing.exit(), missing.text());
        assertFalse(missing.text().contains(Fixture.store().meta().line()),
                "the tier line belongs to the run, not to each symbol: " + missing.text());
        assertTrue(missing.text().contains("fx.NoSuchTypeHere"), missing.text());
    }

    @Test
    @DisplayName("any hit makes the run a success, because the useful question is 'did I learn anything'")
    void anyHitIsSuccess() {
        assertEquals(Answer.OK, QueryCmd.combine(List.of(Answer.NOTHING_FOUND, Answer.OK)));
        assertEquals(Answer.OK, QueryCmd.combine(List.of(Answer.OK, Answer.AMBIGUOUS)));
    }

    @Test
    @DisplayName("a broken index outranks a hit: it invalidates the whole batch, not just the empty ones")
    void notIndexedOutranksAHit() {
        assertEquals(Answer.NOT_INDEXED, QueryCmd.combine(List.of(Answer.OK, Answer.NOT_INDEXED)));
    }

    @Test
    @DisplayName("with no hits the most informative failure wins, ambiguity over emptiness")
    void worstFailureSurfaces() {
        assertEquals(Answer.AMBIGUOUS, QueryCmd.combine(List.of(Answer.NOTHING_FOUND, Answer.AMBIGUOUS)));
        assertEquals(Answer.DEGRADED_EMPTY,
                QueryCmd.combine(List.of(Answer.NOTHING_FOUND, Answer.DEGRADED_EMPTY)));
        assertEquals(Answer.NOTHING_FOUND, QueryCmd.combine(List.of(Answer.NOTHING_FOUND)));
    }

    /**
     * Includes api and tree, which take a scope rather than a symbol and were left single-valued by
     * the first pass. A headless trial run then tried {@code jacli api -T <coords> A B C} unprompted
     * and got "Unmatched arguments from index 4" — the help promised several, the most-used verb of
     * all did not deliver it, and the caller met a usage error at the moment it was cooperating.
     */
    @Test
    @DisplayName("every verb that takes a positional accepts more than one of them")
    void everySymbolVerbTakesMany() {
        var subs = JacliCli.commandLine().getSubcommands();
        for (var verb : List.of("def", "refs", "src", "callers", "callees", "impls", "at", "api", "tree")) {
            var positional = subs.get(verb).getCommandSpec().positionalParameters();
            assertFalse(positional.isEmpty(), verb + " has no positional parameter");
            assertTrue(positional.getFirst().arity().max() > 1,
                    verb + " still takes only one: arity " + positional.getFirst().arity());
        }
    }

    @Test
    @DisplayName("api with several scopes answers each and folds the shared banner once")
    void apiTakesSeveralScopes() {
        Fmt.beginAnswer();
        var first = Discover.api(Fixture.store(), "fx.Circle", null, false, OPTS).text();
        var second = Discover.api(Fixture.store(), "fx.Shapes", null, false, OPTS).text();
        assertTrue(first.contains(Fixture.store().meta().line()), first);
        assertFalse(second.contains(Fixture.store().meta().line()), second);
        assertTrue(second.contains("fx.Shapes"), second);
    }
}
