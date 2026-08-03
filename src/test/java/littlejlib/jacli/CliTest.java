package littlejlib.jacli;

import module java.base;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The tutorial's last line tells the reader to run {@code jacli <verb> --help}, and every verb answered
 * exit 2, "Unknown option". The standard help mixin was declared on the top command only, so picocli
 * never gave the subcommands a {@code -h} — a documented discovery path that did not exist.
 * <p>
 * Asserted over EVERY subcommand rather than a sampled one, because the defect was per-command and a
 * verb added later would inherit it silently.
 */
class CliTest {

    @Test
    @DisplayName("every verb accepts --help and -h")
    void everyVerbHasHelp() {
        var subs = JacliCli.commandLine().getSubcommands();
        assertFalse(subs.isEmpty(), "no subcommands were registered at all");
        for (var e : subs.entrySet()) {
            var spec = e.getValue().getCommandSpec();
            assertNotNull(spec.findOption("--help"), e.getKey() + " has no --help");
            assertNotNull(spec.findOption("-h"), e.getKey() + " has no -h");
        }
    }

    @Test
    @DisplayName("--help on a verb exits 0 and prints that verb's usage")
    void verbHelpExitsZero() {
        var out = new StringWriter();
        var cli = JacliCli.commandLine();
        cli.setOut(new PrintWriter(out));
        assertEquals(0, cli.execute("refs", "--help"), out.toString());
        assertTrue(out.toString().contains("jacli refs"), out.toString());
    }

    @Test
    @DisplayName("the help option is usage-help, not the standard mixin whose -V prints nothing here")
    void helpIsNotTheVersionMixin() {
        var spec = JacliCli.commandLine().getSubcommands().get("tree").getCommandSpec();
        assertTrue(spec.findOption("--help").usageHelp(), "must be a usage-help option");
        assertNull(spec.findOption("-V"), "a subcommand carries no version, so -V would print nothing");
    }

    @Test
    @DisplayName("discover is registered, since the tutorial now sends readers to it first")
    void discoverIsRegistered() {
        assertTrue(JacliCli.commandLine().getSubcommands().containsKey("discover"));
    }
}
