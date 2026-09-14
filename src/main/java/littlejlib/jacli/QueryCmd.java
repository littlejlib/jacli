package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * Shared plumbing for every verb that takes a symbol. Exit codes are part of the contract:
 * 0 found, 1 nothing found, 2 bad usage, 3 ambiguous spec, 4 not indexed,
 * 5 nothing found but the index is degraded so absence is not established.
 * <p>
 * SEVERAL SYMBOLS MAY BE GIVEN AT ONCE, and that is not a convenience — it is what makes this tool
 * competitive with a text search. The real question during a refactor is almost never about one name:
 * the greps it has to beat look like {@code ChatPoster|CdpIdentityResolver|YtAuth|LiveChatModerator},
 * six related identifiers in a single call. Answering those one invocation at a time lost on round
 * trips however much it won on accuracy. The store is opened once and reused for the whole list, so
 * the second symbol and the sixth cost a lookup each rather than an index load each.
 */
public abstract class QueryCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", arity = "1..*", paramLabel = "<symbol>",
            description = {
                "Type, pkg.Type, Type#member, Type#method(int,String), or the dotted form a stack trace prints.",
                "Several may be given: they are answered together against one index load." })
    List<String> symbols = new ArrayList<>();

    protected abstract Answer answer(Store store, String symbol, Opts opts);

    @Override
    public Integer call() {
        try {
            Fmt.beginAnswer();
            var store = common.store();
            var opts = common.opts();
            var text = new StringBuilder();
            var exits = new ArrayList<Integer>();
            for (var symbol : symbols) {
                var answer = answer(store, symbol, opts);
                if (!exits.isEmpty()) text.append('\n');
                text.append(answer.text());
                exits.add(answer.exit());
            }
            common.emit(text + common.scopeHint(store));
            return combine(exits);
        } catch (IllegalStateException e) {
            System.err.println("jacli: " + e.getMessage());
            return Answer.NOT_INDEXED;
        } finally {
            if (common.ownsDb()) littlejlib.jacli.index.Db.shutdown();
        }
    }

    /**
     * One exit code for a run that may have asked several questions. "Did I learn anything" is the
     * useful predicate for a caller, so any hit makes the run a success even when a sibling symbol was
     * a typo — the text names which one failed, and a script that wanted per-symbol certainty should
     * ask per symbol. A broken index is the exception: it invalidates every answer in the batch, not
     * just the ones that came back empty, so it wins over a hit.
     */
    static int combine(List<Integer> exits) {
        if (exits.isEmpty()) return Answer.NOTHING_FOUND;
        if (exits.contains(Answer.NOT_INDEXED)) return Answer.NOT_INDEXED;
        if (exits.contains(Answer.OK)) return Answer.OK;
        if (exits.contains(Answer.AMBIGUOUS)) return Answer.AMBIGUOUS;
        if (exits.contains(Answer.DEGRADED_EMPTY)) return Answer.DEGRADED_EMPTY;
        return exits.getFirst();
    }
}
