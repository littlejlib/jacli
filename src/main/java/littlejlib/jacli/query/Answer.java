package littlejlib.jacli.query;

public record Answer(int exit, String text) {

    public static final int OK = 0, NOTHING_FOUND = 1, USAGE = 2, AMBIGUOUS = 3, NOT_INDEXED = 4, DEGRADED_EMPTY = 5;

    public static Answer ok(String text) { return new Answer(OK, text); }

    public static Answer nothing(String text) { return new Answer(NOTHING_FOUND, text); }

    public static Answer ambiguous(String text) { return new Answer(AMBIGUOUS, text); }

    public static Answer degradedEmpty(String text) { return new Answer(DEGRADED_EMPTY, text); }
}
