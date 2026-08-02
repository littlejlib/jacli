package littlejlib.jacli.query;

import module java.base;
import littlejlib.jacli.core.*;

record Edge(String key, String file, int line) {

    String label(Store store) {
        var d = store.declByKey(key).orElse(null);
        var where = d != null ? Src.abs(store.root(), d.file()) + ":" + d.line()
                : Src.abs(store.root(), file) + ":" + line;
        return (d != null ? d.signature() : SymbolKey.shortForm(key)) + "   " + where;
    }
}
