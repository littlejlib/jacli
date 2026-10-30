package littlejlib.jacli.claude;

import module java.base;
import com.google.gson.JsonObject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "print", description = "Print the agent prompt and the hook definitions that install would write.")
public final class ClaudePrint implements Callable<Integer> {

    @Option(names = "--prompt", description = "Only the prompt.")
    boolean prompt;

    @Option(names = "--hooks", description = "Only the hooks, as the settings.json fragment.")
    boolean hooks;

    @Override
    public Integer call() {
        var both = prompt == hooks;
        if (both || prompt) System.out.print(Kit.prompt());
        if (both) System.out.println("\n# hooks, merged into settings.json:\n");
        if (both || hooks) {
            var fragment = new JsonObject();
            fragment.add("hooks", Kit.hooks());
            System.out.print(Settings.json(fragment));
        }
        return 0;
    }
}
