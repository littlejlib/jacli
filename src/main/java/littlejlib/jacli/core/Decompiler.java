package littlejlib.jacli.core;

import module java.base;
import littlejlib.jacli.proj.Home;
import org.benf.cfr.reader.api.CfrDriver;

/**
 * Source for a class that ships without any, so {@code src} can show a body from a jar.
 * <p>
 * Signatures never need this — the compiler reads those from bytecode exactly, which is why the API
 * surface of a jar-only dependency is produced without a decompiler. A BODY is the one thing bytecode
 * cannot hand over as source, and that is the whole of what CFR is here for.
 * <p>
 * Decompiled output is cached against the jar's size and modification time, because decompiling is
 * seconds where reading a file is milliseconds.
 */
public final class Decompiler {

    private Decompiler() { }

    public static final String CACHE = "decompiled";

    /** Java source for one binary type name, or null when it could not be produced. */
    public static String source(Path jar, String binaryName, Consumer<String> log) {
        var cached = cacheFile(jar, binaryName);
        var stamp = stampOf(jar);
        var existing = readIfFresh(cached, stamp);
        if (existing != null) return existing;

        var entry = binaryName.replace('.', '/') + ".class";
        Path extracted = null;
        try {
            extracted = extract(jar, entry);
            if (extracted == null) {
                log.accept(entry + " is not in " + jar.getFileName());
                return null;
            }
            var sink = new CfrSink();
            var driver = new CfrDriver.Builder()
                    .withOutputSink(sink)
                    .withOptions(Map.of("extraclasspath", jar.toString(), "comments", "false"))
                    .build();
            driver.analyse(List.of(extracted.toString()));
            var java = sink.java().strip();
            if (java.isEmpty()) {
                log.accept("cfr produced nothing for " + binaryName
                        + (sink.problems().isEmpty() ? "" : ": " + sink.problems().getFirst()));
                return null;
            }
            write(cached, stamp + "\n" + java);
            return java;
        } catch (IOException | RuntimeException e) {
            log.accept("could not decompile " + binaryName + ": " + e);
            return null;
        } finally {
            deleteQuietly(extracted);
        }
    }

    static Path extract(Path jar, String entryName) throws IOException {
        try (var zf = new java.util.zip.ZipFile(jar.toFile())) {
            var entry = zf.getEntry(entryName);
            if (entry == null) return null;
            var temp = Files.createTempFile("jacli-cfr-", ".class");
            try (var in = zf.getInputStream(entry)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            return temp;
        }
    }

    static Path cacheFile(Path jar, String binaryName) {
        return Home.file(CACHE, Home.token(jar), binaryName.replace('.', '_') + ".java");
    }

    static String stampOf(Path jar) {
        try {
            var a = Files.readAttributes(jar, BasicFileAttributes.class);
            return "# cfr " + a.size() + " " + a.lastModifiedTime().toMillis();
        } catch (IOException e) {
            return "# cfr unknown";
        }
    }

    static String readIfFresh(Path file, String stamp) {
        try {
            if (!Files.isRegularFile(file)) return null;
            var text = Files.readString(file, StandardCharsets.UTF_8);
            var nl = text.indexOf('\n');
            if (nl < 0 || !text.substring(0, nl).equals(stamp)) return null;
            return text.substring(nl + 1);
        } catch (IOException e) {
            return null;
        }
    }

    static void write(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // a cache that cannot be written only costs time next call
        }
    }

    static void deleteQuietly(Path p) {
        try {
            if (p != null) Files.deleteIfExists(p);
        } catch (IOException e) {
            // a temp file left behind is the OS's problem
        }
    }
}
