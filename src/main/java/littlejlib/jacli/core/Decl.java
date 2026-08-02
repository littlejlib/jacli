package littlejlib.jacli.core;

import module java.base;

/**
 * A declaration found in source, with the span it occupies so a source view is a file slice and
 * needs no javac at query time. {@code superKeys} carries direct supertypes for a type and
 * overridden members for a method, which is what {@code impls} and {@code --overrides} traverse.
 */
public record Decl(String key, DeclKind kind, String name, String ownerKey, String file,
                   int line, int col, int endLine,
                   String signature, String modifiers, String returnType, List<String> paramTypes,
                   List<String> superKeys, String doc, boolean implicit) {

    /** Declared in source, with a real span. */
    public Decl(String key, DeclKind kind, String name, String ownerKey, String file, int line, int col,
                int endLine, String signature, String modifiers, String returnType, List<String> paramTypes,
                List<String> superKeys, String doc) {
        this(key, kind, name, ownerKey, file, line, col, endLine, signature, modifiers, returnType,
                paramTypes, superKeys, doc, false);
    }

    public String at() { return file + ":" + line + ":" + col; }

    public boolean isPublicApi() {
        return modifiers.contains("public") || modifiers.contains("protected");
    }

    public boolean resolved() { return key != null && !key.startsWith(Keys.UNRESOLVED); }
}
