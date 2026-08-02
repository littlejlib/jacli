package littlejlib.jacli;

import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;

@Command(name = "callees", description = "What this method calls, transitively.",
        footer = {
            "",
            "  jacli callees HtmlRenderer#render -d 2",
            "",
            "Only calls that resolve to something inside the index are followed, so a chain that leaves",
            "the project stops there."
        })
public final class CalleesCmd extends QueryCmd {

    @Override
    protected Answer answer(Store store, String symbol, Opts opts) {
        return Graph.callees(store, symbol, opts);
    }
}
