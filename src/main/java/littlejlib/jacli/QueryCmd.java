package littlejlib.jacli;

import module java.base;
import littlejlib.jacli.query.*;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/**
 * Shared plumbing for every verb that takes a symbol. Exit codes are part of the contract:
 * 0 found, 1 nothing found, 2 bad usage, 3 ambiguous spec, 4 not indexed,
 * 5 nothing found but the index is degraded so absence is not established.
 */
public abstract class QueryCmd implements Callable<Integer> {

    @Mixin
    Common common;

    @Parameters(index = "0", paramLabel = "<symbol>",
            description = "Type, pkg.Type, Type#member, Type#method(int,String), or the dotted form a stack trace prints.")
    String symbol;

    protected abstract Answer answer(Store store, String symbol, Opts opts);

    @Override
    public Integer call() {
        try {
            var store = common.store();
            var answer = answer(store, symbol, common.opts());
            common.emit(answer.text() + common.scopeHint(store));
            return answer.exit();
        } catch (IllegalStateException e) {
            System.err.println("jacli: " + e.getMessage());
            return Answer.NOT_INDEXED;
        } finally {
            if (common.ownsDb()) littlejlib.jacli.index.Db.shutdown();
        }
    }
}
