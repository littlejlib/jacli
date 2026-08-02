package littlejlib.jacli;

import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;

@Command(name = "def", description = "Where a symbol is declared, with its signature, javadoc and use count.",
        footer = {
            "",
            "  jacli def Out_I            the type, plus how many references and subtypes it has",
            "  jacli def Out_I#ba         every overload, each with its own counts",
            "",
            "Use src instead when you want the body rather than the signature."
        })
public final class DefCmd extends QueryCmd {

    @Override
    protected Answer answer(Store store, String symbol, Opts opts) {
        return Verbs.def(store, symbol, opts);
    }
}
