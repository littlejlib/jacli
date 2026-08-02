package littlejlib.jacli;

import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;

@Command(name = "refs", description = "Find every use of a symbol, resolved by the compiler.",
        footer = {
            "",
            "This is the verb that replaces a grep for an identifier. It knows a declaration from a call",
            "from a comment from a string literal, picks the right overload, and reports the kind of each",
            "use (CALL, TYPE, FIELD, EXTENDS, IMPLEMENTS, NEW, METHOD_REF, IMPORT, THROWS, ANNOTATION).",
            "",
            "  jacli refs Out_I                      every use of the type",
            "  jacli refs Out_I#ba                   every use of the member, all overloads",
            "  jacli refs 'Out_I#ba(byte[])'         one overload only",
            "  jacli refs Out_I --kind EXTENDS,IMPLEMENTS    only where it is subtyped",
            "  jacli refs Cache#deleteTree --overrides       include the override chain",
            "",
            "A symbol declared in a dependency still works, because references are indexed by target",
            "key: jacli refs java.util.stream.Collectors#joining."
        })
public final class RefsCmd extends QueryCmd {

    @Override
    protected Answer answer(Store store, String symbol, Opts opts) {
        return Verbs.refs(store, symbol, opts);
    }
}
