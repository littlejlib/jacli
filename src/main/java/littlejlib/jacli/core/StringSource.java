package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;

/**
 * An in-memory compilation unit. Needed because reading a jar's API through {@code Elements} still
 * requires a task to hang the symbol table on, and there is no real source file to give it.
 */
public final class StringSource extends SimpleJavaFileObject {

    final String code;

    public StringSource(String name, String code) {
        super(URI.create("string:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    public static StringSource probe() {
        return new StringSource("JacliProbe", "final class JacliProbe { }");
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
