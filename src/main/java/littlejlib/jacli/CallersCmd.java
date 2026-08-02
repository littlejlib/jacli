package littlejlib.jacli;

import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;

@Command(name = "callers", description = "Who calls this method, transitively.",
        footer = {
            "",
            "  jacli callers Cache#deleteTree              direct callers",
            "  jacli callers Cache#deleteTree -d 3         three levels up",
            "  jacli callers Out_I#ba --overrides          count calls through implementations too",
            "",
            "This is the question to ask before changing a signature."
        })
public final class CallersCmd extends QueryCmd {

    @Override
    protected Answer answer(Store store, String symbol, Opts opts) {
        return Graph.callers(store, symbol, opts);
    }
}
