package littlejlib.jacli;

import module java.base;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** install and update act on the exe this process runs from, so the daemon must never be asked to run them. */
class SelfManageTest {

    @Test
    void installAndUpdateRunInTheClient() {
        assertTrue(JacliCli.runsLocally(List.of("install")));
        assertTrue(JacliCli.runsLocally(List.of("update", "--check")));
        assertFalse(JacliCli.runsLocally(List.of("refs", "Foo")));
    }

    @Test
    void theNoticeIsLeftOutOfManagementAndHooks() {
        assertFalse(JacliCli.wantsNotice(List.of("update")));
        assertFalse(JacliCli.wantsNotice(List.of("claude", "hook", "user-prompt")));
        assertFalse(JacliCli.wantsNotice(List.of("--version")));
        assertTrue(JacliCli.wantsNotice(List.of("refs", "Foo")));
        assertTrue(JacliCli.wantsNotice(List.of()));
    }

    @Test
    void versionIsTheProjectVersion() {
        var v = littlejlib.jacli.release.Version.number();
        assertTrue(v.matches("\\d+(\\.\\d+)+"), v);
    }
}
