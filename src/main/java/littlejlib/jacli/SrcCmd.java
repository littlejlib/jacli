package littlejlib.jacli;

import littlejlib.jacli.query.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "src", description = "Print a declaration's source, sliced straight from the file.",
        footer = {
            "",
            "Every declaration carries its span in the index, so this is one file read and no compilation.",
            "",
            "  jacli src HtmlRenderer#renderElement          one method, whatever its overloads",
            "  jacli src HtmlRenderer#renderElement --doc     include the javadoc above it",
            "  jacli src luvml.o.HtmlRenderer.renderElement   the dotted form a stack trace gives you",
            "  jacli src Out_A --numbered                     a whole type with line numbers",
            "",
            "Walking a stack trace is the intended use: pass each frame in turn and read the bodies",
            "without opening any files."
        })
public final class SrcCmd extends QueryCmd {

    @Option(names = "--doc", description = "Include the javadoc or comment block above the declaration.")
    boolean doc;

    @Option(names = { "-n", "--numbered" }, description = "Prefix each line with its line number.")
    boolean numbered;

    @Override
    protected Answer answer(Store store, String symbol, Opts opts) {
        return Verbs.src(store, symbol, opts.withDoc(doc).withNumbered(numbered));
    }
}
