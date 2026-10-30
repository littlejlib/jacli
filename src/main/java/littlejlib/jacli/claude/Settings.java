package littlejlib.jacli.claude;

import module java.base;
import com.google.gson.*;

/**
 * Merges jacli's hooks into a Claude Code settings.json and takes them out again. Every other key, every
 * other hook and the order of both are left as found: Gson's tree keeps insertion order and number text,
 * and HTML escaping is off, so a file that already holds our current hooks is never rewritten.
 */
final class Settings {

    private Settings() { }

    static JsonObject read(Path file) throws IOException {
        if (!Files.exists(file)) return new JsonObject();
        var text = Files.readString(file);
        return text.isBlank() ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
    }

    static String json(JsonObject root) { return Kit.GSON.toJson(root) + "\n"; }

    static JsonObject hooksOf(JsonObject root) {
        var h = root.get("hooks");
        return h != null && h.isJsonObject() ? h.getAsJsonObject() : null;
    }

    /** {@code event|matcher|command} for every command hook, in file order. */
    static List<String> entries(JsonObject hooks) {
        var out = new ArrayList<String>();
        if (hooks == null) return out;
        hooks.entrySet().forEach(ev -> groups(ev.getValue()).forEach(g -> commands(g).forEach(h ->
                out.add(ev.getKey() + "|" + matcher(g) + "|" + h.get("command").getAsString()))));
        return out;
    }

    static List<String> ours(JsonObject root) { return entries(hooksOf(root)).stream().filter(Settings::isOurs).toList(); }

    static List<String> foreign(JsonObject root) {
        return entries(hooksOf(root)).stream().filter(e -> e.contains("jacli") && !isOurs(e)).toList();
    }

    static List<String> expected() { return entries(Kit.hooks()); }

    static boolean current(JsonObject root) { return sorted(ours(root)).equals(sorted(expected())); }

    static boolean strip(JsonObject root) {
        var hooks = hooksOf(root);
        if (hooks == null) return false;
        var removed = false;
        for (var event : List.copyOf(hooks.keySet())) {
            var groups = hooks.get(event);
            if (!groups.isJsonArray()) continue;
            var arr = groups.getAsJsonArray();
            var here = false;
            for (var i = arr.size() - 1; i >= 0; i--) {
                if (!arr.get(i).isJsonObject()) continue;
                var list = arr.get(i).getAsJsonObject().get("hooks");
                if (list == null || !list.isJsonArray()) continue;
                var hs = list.getAsJsonArray();
                var before = hs.size();
                for (var j = hs.size() - 1; j >= 0; j--)
                    if (isOurs(hs.get(j))) hs.remove(j);
                if (hs.size() == before) continue;
                removed = here = true;
                if (hs.isEmpty()) arr.remove(i);
            }
            if (here && arr.isEmpty()) hooks.remove(event);
        }
        if (removed && hooks.isEmpty()) root.remove("hooks");
        return removed;
    }

    static void add(JsonObject root) {
        var hooks = hooksOf(root);
        if (hooks == null) root.add("hooks", hooks = new JsonObject());
        for (var ev : Kit.hooks().entrySet()) {
            var arr = hooks.has(ev.getKey()) && hooks.get(ev.getKey()).isJsonArray()
                    ? hooks.getAsJsonArray(ev.getKey()) : new JsonArray();
            ev.getValue().getAsJsonArray().forEach(g -> arr.add(g.deepCopy()));
            hooks.add(ev.getKey(), arr);
        }
    }

    static boolean isOurs(String entry) { return entry.contains(Kit.MARK); }

    static boolean isOurs(JsonElement hook) {
        return hook.isJsonObject() && hook.getAsJsonObject().has("command")
                && hook.getAsJsonObject().get("command").getAsString().contains(Kit.MARK);
    }

    static List<JsonObject> groups(JsonElement e) {
        if (e == null || !e.isJsonArray()) return List.of();
        return e.getAsJsonArray().asList().stream().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).toList();
    }

    static List<JsonObject> commands(JsonObject group) {
        return groups(group.get("hooks")).stream().filter(h -> h.has("command") && h.get("command").isJsonPrimitive()).toList();
    }

    static String matcher(JsonObject group) {
        var m = group.get("matcher");
        return m != null && m.isJsonPrimitive() ? m.getAsString() : "";
    }

    static List<String> sorted(List<String> l) { return l.stream().sorted().toList(); }
}
