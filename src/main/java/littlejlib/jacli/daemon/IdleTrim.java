package littlejlib.jacli.daemon;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/// After 15 quiet seconds the working set is emptied (Windows), and after a longer spell one full GC
/// comes first, so what stays committed is paged out instead of held in RAM. The next request pages
/// back what it needs.
final class IdleTrim {

    static final Duration CHECK = Duration.ofSeconds(10), QUICK = Duration.ofSeconds(15);

    static volatile Instant worked = Instant.now();

    /** Background work counts as activity, or the trim runs before it and never after. */
    static void touched() {
        worked = Instant.now();
    }

    static void start(Supplier<Instant> lastActivity, Duration idleFor, Runnable onIdle) {
        Thread.ofPlatform().name("jacli-idle-trim").daemon().start(() -> {
            var trimmedFor = Instant.EPOCH;
            var pagedOutFor = Instant.EPOCH;
            while (true) {
                try { Thread.sleep(CHECK.toMillis()); } catch (InterruptedException e) { return; }
                var last = lastActivity.get();
                if (last.equals(trimmedFor)) continue;
                var idle = Duration.between(last, Instant.now());
                if (idle.compareTo(idleFor) < 0) {
                    if (idle.compareTo(QUICK) >= 0 && !last.equals(pagedOutFor)) { pageOut(); pagedOutFor = last; }
                    continue;
                }
                onIdle.run();
                trim();
                trimmedFor = last;
            }
        });
    }

    static void trim() {
        System.gc();
        pageOut();
    }

    /** Cheap, so it can follow every burst of requests: what was touched leaves RAM and returns on demand. */
    static void pageOut() {
        if (EMPTY != null) try { EMPTY.invoke(CURRENT.invoke(), -1L, -1L); } catch (Throwable ignored) { }
    }

    static final MethodHandle CURRENT, EMPTY;

    static {
        MethodHandle c = null, e = null;
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            var k32 = SymbolLookup.libraryLookup("kernel32.dll", Arena.global());
            var linker = Linker.nativeLinker();
            c = linker.downcallHandle(k32.find("GetCurrentProcess").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS));
            e = linker.downcallHandle(k32.find("SetProcessWorkingSetSize").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        }
        CURRENT = c;
        EMPTY = e;
    }

    private IdleTrim() { }
}
