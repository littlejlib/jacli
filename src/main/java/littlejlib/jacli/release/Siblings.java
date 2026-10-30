package littlejlib.jacli.release;

import module java.base;
import jarrunner.jr.apputils.JrApp;
import littlejlib.jacli.daemon.Launcher;

/** The exes that travel with jacli.exe: the daemon (its own process name and memory flags) and the old symbols name. */
public final class Siblings {

    public static final List<String> NAMES = List.of(Launcher.DAEMON_EXE, "symbols.exe");

    static final String RELEASES = "https://github.com/littlejlib/jacli/releases/download/v";

    private Siblings() { }

    /** The siblings that exist in a folder. */
    static List<Path> in(Path dir) {
        return NAMES.stream().map(dir::resolve).filter(Files::isRegularFile).toList();
    }

    /** Puts every sibling into {@code to}: copied from beside the running exe when it is there, else fetched from this version's release. */
    static List<String> place(Path from, Path to, Consumer<String> say) {
        var problems = new ArrayList<String>();
        for (var name : NAMES) {
            try {
                var source = from.resolve(name);
                var target = to.resolve(name);
                if (Files.isRegularFile(target) && Files.isRegularFile(source) && Files.isSameFile(source, target)) continue;
                if (Files.isRegularFile(source)) put(Files.readAllBytes(source), target);
                else put(fetch(name), target);
                say.accept("Installed " + target);
            } catch (IOException e) {
                problems.add(name + ": " + e.getMessage());
            }
        }
        return problems;
    }

    static byte[] fetch(String name) throws IOException {
        var version = JrApp.appVersion().orElseThrow(() -> new IOException("this exe does not know its version"));
        var base = RELEASES + version + "/";
        var expected = sumOf(name, get(base + "SHA256SUMS"));
        var bytes = get(base + name);
        if (!sha256(bytes).equals(expected)) throw new IOException("the download of " + name + " does not match SHA256SUMS");
        return bytes;
    }

    static String sumOf(String name, byte[] sums) throws IOException {
        for (var line : new String(sums, StandardCharsets.UTF_8).split("\n")) {
            var parts = line.trim().split("\\s+", 2);
            if (parts.length == 2 && parts[1].equals(name)) return parts[0].toLowerCase(Locale.ROOT);
        }
        throw new IOException("SHA256SUMS does not list " + name);
    }

    /** Four tries a second or more apart: a connection that drops mid-install must not leave jacli without its daemon. */
    static byte[] get(String url) throws IOException {
        return retrying(4, 1000, () -> once(url));
    }

    static <T> T retrying(int tries, long pauseMs, Callable<T> action) throws IOException {
        for (var attempt = 1; ; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                if (attempt >= tries) throw e instanceof IOException io ? io : new IOException(e);
                try {
                    Thread.sleep(pauseMs * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", interrupted);
                }
            }
        }
    }

    static byte[] once(String url) throws IOException {
        var c = URI.create(url).toURL().openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(60_000);
        try (var in = c.getInputStream()) {
            return in.readAllBytes();
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Written beside the target and renamed in; an exe in use is set aside the way jr does, to be deleted on a later launch. */
    static void put(byte[] bytes, Path target) throws IOException {
        var tmp = target.resolveSibling(target.getFileName() + ".jr-new");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException busy) {
            var aside = target.resolveSibling(target.getFileName() + ".jr-replaced");
            Files.deleteIfExists(aside);
            Files.move(target, aside);
            Files.move(tmp, target);
        }
    }
}
