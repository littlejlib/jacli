package littlejlib.jacli.claude;

import module java.base;
import com.google.gson.*;

/**
 * Everything {@code jacli claude install} writes is embedded here as a resource, so a jar on a machine that
 * has never seen this repository carries its own prompt and hooks. The marker is how install, uninstall and
 * status recognise their own hook entries among the user's, without a manifest file to fall out of step.
 */
final class Kit {

    private Kit() { }

    static final String MARK = "jacli claude hook";
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    static String text(String name) {
        try (var in = Kit.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String prompt() { return text("prompt.md"); }

    static JsonObject hooks() { return JsonParser.parseString(text("hooks.json")).getAsJsonObject(); }
}
