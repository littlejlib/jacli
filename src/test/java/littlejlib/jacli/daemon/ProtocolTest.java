package littlejlib.jacli.daemon;

import module java.base;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Progress notes on the wire.
 * <p>
 * The first call in an unindexed project took 31 seconds during which nothing was sent at all, since
 * the daemon computed the whole reply and wrote one frame at the end. A caller cannot tell that from
 * a hang. Notes are extra frames ahead of the response, so the reader has to loop — and a reader that
 * assumed the first frame was the answer is exactly the bug these pin.
 */
class ProtocolTest {

    @Test
    @DisplayName("a response with no notes reads exactly as it always did")
    void responseWithoutNotes() throws IOException {
        var buf = new ByteArrayOutputStream();
        Protocol.writeResponse(buf, 0, "stamp", "out", "err");
        var got = Protocol.readResponse(new ByteArrayInputStream(buf.toByteArray()));
        assertEquals(0, got.exit());
        assertEquals("out", got.stdout());
        assertEquals("err", got.stderr());
    }

    @Test
    @DisplayName("notes are delivered in order, before the response, and are not mistaken for it")
    void notesPrecedeTheResponse() throws IOException {
        var buf = new ByteArrayOutputStream();
        Protocol.writeNote(buf, "first call in X");
        Protocol.writeNote(buf, "compiling 41 java files");
        Protocol.writeResponse(buf, 0, "stamp", "the answer", "");
        var seen = new ArrayList<String>();
        var got = Protocol.readResponse(new ByteArrayInputStream(buf.toByteArray()), seen::add);
        assertEquals(List.of("first call in X", "compiling 41 java files"), seen);
        assertEquals("the answer", got.stdout());
    }

    @Test
    @DisplayName("a note is not silently folded into stdout, which would corrupt a piped answer")
    void notesStayOffStdout() throws IOException {
        var buf = new ByteArrayOutputStream();
        Protocol.writeNote(buf, "progress");
        Protocol.writeResponse(buf, 0, "stamp", "payload", "");
        var got = Protocol.readResponse(new ByteArrayInputStream(buf.toByteArray()), n -> { });
        assertEquals("payload", got.stdout());
        assertFalse(got.stdout().contains("progress"));
        assertFalse(got.stderr().contains("progress"));
    }

    @Test
    @DisplayName("unicode survives a note, since a project path can hold anything")
    void unicodeInNotes() throws IOException {
        var buf = new ByteArrayOutputStream();
        var text = "first call in C:/user/code/\u00e9\u00e0\u4e2d\u6587 - indexing";
        Protocol.writeNote(buf, text);
        Protocol.writeResponse(buf, 0, "stamp", "", "");
        var seen = new ArrayList<String>();
        Protocol.readResponse(new ByteArrayInputStream(buf.toByteArray()), seen::add);
        assertEquals(List.of(text), seen);
    }

    @Test
    @DisplayName("the note magic cannot be confused with the response magic")
    void magicsAreDistinct() {
        assertNotEquals(Protocol.MAGIC, Protocol.NOTE);
        assertTrue(Protocol.NOTE.startsWith(Protocol.MAGIC),
                "kept as a prefix so one readLine can classify both");
    }

    @Test
    @DisplayName("a truncated note frame fails loudly instead of being read as an answer")
    void truncatedNoteFails() {
        var head = (Protocol.NOTE + " 100\n").getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class,
                () -> Protocol.readResponse(new ByteArrayInputStream(head), n -> { }));
    }
}
