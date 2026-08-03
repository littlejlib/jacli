package littlejlib.jacli.daemon;

import module java.base;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A query {@code --scope}d over more projects than the cap used to evict its own working set while it
 * was still running, so every scoped call re-swept every member and the daemon did nothing for the case
 * it exists to serve.
 * <p>
 * In this package because the fix turns on {@code lastUsed}, and ageing a project by hand is the only
 * way to test the other half — that recent use earning a reprieve did not quietly remove the bound
 * altogether. Both halves are asserted here for that reason.
 */
class HotTest {

    static Hot hot(List<String> evicted) throws IOException {
        return new Hot(evicted::add);
    }

    static void age(Hot h, String pid, Duration by) {
        h.get(pid).lastUsed -= by.toMillis();
    }

    @Test
    @DisplayName("a scope wider than the cap keeps every member: recent use is use")
    void scopeWiderThanCapStaysWarm(@TempDir Path dir) throws IOException {
        var evicted = new ArrayList<String>();
        var h = hot(evicted);
        for (var i = 0; i < Hot.MAX_HOT + 3; i++) h.touch(dir, "p" + i, false);
        assertEquals(Hot.MAX_HOT + 3, h.all().size(), "nothing touched moments ago may be evicted");
        assertEquals(List.of(), evicted);
    }

    @Test
    @DisplayName("the soft cap still applies once the grace window has passed")
    void softCapAppliesAfterGrace(@TempDir Path dir) throws IOException {
        var evicted = new ArrayList<String>();
        var h = hot(evicted);
        for (var i = 0; i < Hot.MAX_HOT; i++) h.touch(dir, "old" + i, false);
        for (var i = 0; i < Hot.MAX_HOT; i++) age(h, "old" + i, Hot.GRACE.plusSeconds(5));

        h.touch(dir, "fresh", false);
        assertTrue(evicted.contains("old0"), "the least recently used stale project should go: " + evicted);
        assertEquals(Hot.MAX_HOT, h.all().size(), "back to the cap: " + h.all().size());
        assertNotNull(h.get("fresh"), "the project just asked about must survive");
    }

    @Test
    @DisplayName("the hard ceiling bounds the warm set even when everything is in use")
    void hardCeilingStillBounds(@TempDir Path dir) throws IOException {
        var evicted = new ArrayList<String>();
        var h = hot(evicted);
        for (var i = 0; i < Hot.HARD_MAX + 4; i++) h.touch(dir, "p" + i, false);
        assertEquals(Hot.HARD_MAX, h.all().size(),
                "grace must not let a session hold every project open: " + h.all().size());
        assertFalse(evicted.isEmpty(), "the ceiling has to evict something");
    }

    @Test
    @DisplayName("a pinned project is exempt from both the cap and the ceiling")
    void pinnedSurvives(@TempDir Path dir) throws IOException {
        var evicted = new ArrayList<String>();
        var h = hot(evicted);
        h.touch(dir, "pinned", true);
        age(h, "pinned", Hot.GRACE.plusSeconds(5));
        for (var i = 0; i < Hot.HARD_MAX + 4; i++) h.touch(dir, "p" + i, false);
        assertNotNull(h.get("pinned"), "index --watch is a stated preference: " + evicted);
        assertFalse(evicted.contains("pinned"));
    }

    @Test
    @DisplayName("a project idle past the TTL is released even below the cap")
    void ttlReleasesIdleProjects(@TempDir Path dir) throws IOException {
        var evicted = new ArrayList<String>();
        var h = hot(evicted);
        h.touch(dir, "stale", false);
        age(h, "stale", Hot.TTL.plusMinutes(1));
        h.touch(dir, "other", false);
        assertTrue(evicted.contains("stale"), "past the TTL it should go regardless of the cap: " + evicted);
    }
}
