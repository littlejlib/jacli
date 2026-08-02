package littlejlib.jacli;

import module java.base;

/**
 * Default help is a tutorial, because a CLI has no prompt attached the way an MCP server does — this
 * text is the only documentation an agent will ever read. Held in resources rather than in a string
 * literal, per project convention.
 */
public final class Tutorial {

    private Tutorial() { }

    public static final String FULL = "tutorial.txt", BRIEF = "tutorial-brief.txt";

    public static String full() { return read(FULL); }

    public static String brief() { return read(BRIEF); }

    public static String read(String resource) {
        try (var in = Tutorial.class.getResourceAsStream(resource)) {
            if (in == null) return "jacli: missing help resource " + resource + "\n";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "jacli: help resource " + resource + " unreadable (" + e.getMessage() + ")\n";
        }
    }
}
