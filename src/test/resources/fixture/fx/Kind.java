package fx;

public enum Kind {
    ROUND, ANGULAR;

    /** A field and a method sharing a name: the case where a field's spec cannot be distinguished. */
    public static final String label = "kind";

    public static String label() {
        return label;
    }

    public boolean isRound() {
        return this == ROUND;
    }
}
