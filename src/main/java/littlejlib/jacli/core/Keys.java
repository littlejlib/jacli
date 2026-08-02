package littlejlib.jacli.core;

/**
 * Marker for identities the engine could not resolve. A syntactic key carries only the spelling
 * that appeared in source, so it must never be confused with a resolved one — a wrong equality
 * between {@code ~render} in two unrelated classes is exactly the grep failure jacli exists to fix.
 */
public final class Keys {

    private Keys() { }

    public static final String UNRESOLVED = "~";

    public static String unresolved(String spelling) { return UNRESOLVED + spelling; }

    public static boolean isUnresolved(String key) { return key != null && key.startsWith(UNRESOLVED); }

    public static String spelling(String key) {
        return isUnresolved(key) ? key.substring(UNRESOLVED.length()) : key;
    }
}
