package littlejlib.jacli.claude;

import module java.base;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class InstallerTest {

    static final String SETTINGS = "{\n  \"model\": \"opus\",\n  \"hooks\": {\n    \"PreToolUse\": [\n      {\n        \"matcher\": \"Bash\",\n"
            + "        \"hooks\": [\n          {\n            \"type\": \"command\",\n            \"command\": \"echo a=b <c> & d\",\n"
            + "            \"timeout\": 10\n          }\n        ]\n      }\n    ]\n  },\n  \"cleanupPeriodDays\": 99999\n}\n";
    static final String CLAUDE_MD = "# mine\r\n\r\nkeep this line\r\n";

    @Test
    void installTwiceThenUninstallRestoresEveryByte(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.json"), SETTINGS);
        Files.writeString(dir.resolve("CLAUDE.md"), CLAUDE_MD);
        var p = Place.user(dir);

        assertFalse(new Installer(p, false).install().isEmpty());
        assertTrue(new Installer(p, false).status().current());
        var settings = Files.readString(dir.resolve("settings.json"));
        assertTrue(settings.contains("echo a=b <c> & d"), "other hook kept, unescaped");
        assertTrue(settings.indexOf("\"model\"") < settings.indexOf("\"cleanupPeriodDays\""), "key order kept");
        assertEquals(CLAUDE_MD + "@jacli.md\r\n", Files.readString(dir.resolve("CLAUDE.md")));

        assertEquals(List.of(), new Installer(p, false).install(), "second install changes nothing");

        new Installer(p, false).uninstall();
        assertEquals(SETTINGS, Files.readString(dir.resolve("settings.json")));
        assertEquals(CLAUDE_MD, Files.readString(dir.resolve("CLAUDE.md")));
        assertFalse(Files.exists(dir.resolve("jacli.md")));
        assertTrue(new Installer(p, true).status().absent());
    }

    @Test
    void installIntoNothingAndUninstallLeavesNothing(@TempDir Path dir) throws IOException {
        var p = Place.project(dir);
        new Installer(p, false).install();
        assertTrue(Files.exists(dir.resolve(".claude/settings.json")));
        assertEquals("@.claude/jacli.md\n", Files.readString(dir.resolve("CLAUDE.md")));
        new Installer(p, false).uninstall();
        try (var s = Files.list(dir)) {
            assertEquals(List.of(), s.toList());
        }
    }

    @Test
    void staleHooksAreReplacedNotDuplicated(@TempDir Path dir) throws IOException {
        var p = Place.user(dir);
        new Installer(p, false).install();
        var f = dir.resolve("settings.json");
        Files.writeString(f, Files.readString(f).replace("jacli claude hook pre-grep", "jacli claude hook pre-grep --old"));
        assertFalse(new Installer(p, true).status().current());
        new Installer(p, false).install();
        assertTrue(new Installer(p, true).status().current());
        assertEquals(Settings.expected().size(), Settings.ours(Settings.read(f)).size());
    }

    @Test
    void hookDecisions(@TempDir Path dir) throws IOException {
        assertTrue(Hooks.preBash("find / -name foo.jar").orElseThrow().contains("\"deny\""));
        assertTrue(Hooks.preBash("javap -cp x.jar a.B").orElseThrow().contains("additionalContext"));
        assertTrue(Hooks.preBash("ls -la").isEmpty());
        assertTrue(Hooks.preGrep("implements Exporter", "").isPresent());
        assertTrue(Hooks.preGrep("TODO", "*.java").isEmpty());
        assertTrue(Hooks.userPrompt(dir).isEmpty());
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        assertTrue(Hooks.userPrompt(dir).orElseThrow().contains("UserPromptSubmit"));
    }
}
