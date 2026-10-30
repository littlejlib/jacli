package littlejlib.jacli.release;

import module java.base;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseTest {

    static final String SUMS = "aa11  jacli-daemon.exe\n" + "BB22  symbols.exe\n" + "cc33  jacli.jar\n";

    @Test
    void sumOfFindsTheNamedFile() throws IOException {
        assertEquals("aa11", Siblings.sumOf("jacli-daemon.exe", SUMS.getBytes(StandardCharsets.UTF_8)));
        assertEquals("bb22", Siblings.sumOf("symbols.exe", SUMS.getBytes(StandardCharsets.UTF_8)), "hex is compared in lower case");
        assertThrows(IOException.class, () -> Siblings.sumOf("missing.exe", SUMS.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void siblingsAreCopiedFromBesideTheRunningExe(@TempDir Path dir) throws IOException {
        var from = Files.createDirectory(dir.resolve("build"));
        var to = Files.createDirectory(dir.resolve("installed"));
        for (var name : Siblings.NAMES) Files.writeString(from.resolve(name), "exe " + name);

        var problems = Siblings.place(from, to, s -> { });

        assertEquals(List.of(), problems);
        for (var name : Siblings.NAMES) assertEquals("exe " + name, Files.readString(to.resolve(name)));
        assertEquals(Siblings.NAMES.size(), Siblings.in(to).size());
    }

    @Test
    void placingOverAnExistingSiblingReplacesIt(@TempDir Path dir) throws IOException {
        var target = dir.resolve("jacli-daemon.exe");
        Files.writeString(target, "old");
        Siblings.put("new".getBytes(StandardCharsets.UTF_8), target);
        assertEquals("new", Files.readString(target));
        assertFalse(Files.exists(dir.resolve("jacli-daemon.exe.jr-new")));
    }

    @Test
    void aDroppedConnectionIsRetriedThenGivenUp() throws IOException {
        var calls = new int[1];
        assertEquals("ok", Siblings.retrying(4, 1, () -> {
            if (++calls[0] < 3) throw new IOException("Connect timed out");
            return "ok";
        }));
        assertEquals(3, calls[0]);

        calls[0] = 0;
        var e = assertThrows(IOException.class, () -> Siblings.retrying(3, 1, () -> {
            calls[0]++;
            throw new IOException("still down");
        }));
        assertEquals("still down", e.getMessage());
        assertEquals(3, calls[0]);
    }

    @Test
    void sha256IsHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                Siblings.sha256("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void jrsBatchLineIsReadFromMixedOutput() {
        var out = "downloading...\n{\"status\":\"newer\",\"version\":\"0.2\",\"latest\":\"0.2.1\",\"message\":\"Update available: 0.2 -> 0.2.1\"}\n";
        assertEquals("Update available: 0.2 -> 0.2.1", ExeUpdate.lastLine(out));
        assertEquals("plain text", ExeUpdate.lastLine("plain text\n"));
    }
}
