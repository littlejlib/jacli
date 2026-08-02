package littlejlib.jacli;

import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;

@Command(name = "impls", description = "What extends or implements a type, transitively.",
        footer = {
            "",
            "  jacli impls Out_I         every implementation, including those reached through Out_A",
            "",
            "Indirect implementers are what grep cannot reach: a class implementing Out_I through an",
            "abstract base never spells Out_I anywhere in its own file."
        })
public final class ImplsCmd extends QueryCmd {

    @Override
    protected Answer answer(Store store, String symbol, Opts opts) {
        return Graph.impls(store, symbol, opts);
    }
}
