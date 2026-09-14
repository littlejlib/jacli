package littlejlib.jacli;

import module java.base;
import picocli.CommandLine.Command;

/**
 * The long tutorial, now that bare {@code jacli} prints the short one.
 * <p>
 * The defaults were the wrong way round. Running the tool with no arguments — which is what anyone
 * does first, and what an agent does when it wants to know whether a verb exists — dumped 534 lines.
 * The reader pays for all of it before learning whether the tool is even relevant, so the cheap
 * summary is the better default and the full text is a verb you ask for.
 */
@Command(name = "tutorial", description = "The full tutorial. Bare jacli prints the short version.")
public final class TutorialCmd implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.print(Tutorial.full());
        return 0;
    }
}
