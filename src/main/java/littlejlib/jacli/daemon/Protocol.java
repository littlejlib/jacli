package littlejlib.jacli.daemon;

import module java.base;

/**
 * Loopback framing. Deliberately dumb: the daemon computes exactly the text the CLI should print, so
 * there is nothing to serialise beyond bytes, an exit code, and the build stamp.
 * <p>
 * Arguments are NUL-separated because NUL cannot occur in a command-line argument, while newlines
 * and path separators can.
 */
public final class Protocol {

    private Protocol() { }

    public static final String MAGIC = "JACLI1";
    /**
     * A progress note, sent while the request is still being worked on. Any number may precede the one
     * response frame, so a reader must loop rather than assume the first frame is the answer.
     */
    public static final String NOTE = "JACLI1N";
    public static final char SEP = '\0';
    public static final int MAX_FRAME = 64 << 20;

    public static void writeRequest(OutputStream out, String expectedStamp, String cwd, List<String> args)
            throws IOException {
        var payload = (cwd + SEP + String.join(String.valueOf(SEP), args)).getBytes(StandardCharsets.UTF_8);
        var head = (MAGIC + " " + expectedStamp.replace(' ', '_') + " " + payload.length + "\n")
                .getBytes(StandardCharsets.UTF_8);
        out.write(head);
        out.write(payload);
        out.flush();
    }

    public static Request readRequest(InputStream in) throws IOException {
        var head = readLine(in).split(" ");
        if (head.length < 3 || !MAGIC.equals(head[0])) throw new IOException("bad request frame");
        var payload = new String(readExactly(in, size(head[2])), StandardCharsets.UTF_8);
        var parts = payload.split(String.valueOf(SEP), -1);
        return new Request(head[1], parts[0], List.of(parts).subList(1, parts.length));
    }

    public static void writeResponse(OutputStream out, int exit, String stamp, String stdout, String stderr)
            throws IOException {
        var o = stdout.getBytes(StandardCharsets.UTF_8);
        var e = stderr.getBytes(StandardCharsets.UTF_8);
        out.write((MAGIC + " " + exit + " " + stamp.replace(' ', '_') + " " + o.length + " " + e.length + "\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(o);
        out.write(e);
        out.flush();
    }

    /** Progress, flushed on its own so it reaches the caller while the work is still running. */
    public static void writeNote(OutputStream out, String text) throws IOException {
        var payload = text.getBytes(StandardCharsets.UTF_8);
        out.write((NOTE + " " + payload.length + "\n").getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    public static Response readResponse(InputStream in) throws IOException {
        return readResponse(in, note -> { });
    }

    /**
     * Reads frames until the response arrives, handing every note that precedes it to {@code onNote}
     * as it lands. The note magic is checked first and is a strict prefix of nothing else, so an older
     * daemon that never sends one is read exactly as before.
     */
    public static Response readResponse(InputStream in, Consumer<String> onNote) throws IOException {
        while (true) {
            var head = readLine(in).split(" ");
            if (NOTE.equals(head[0])) {
                if (head.length < 2) throw new IOException("bad note frame");
                onNote.accept(new String(readExactly(in, size(head[1])), StandardCharsets.UTF_8));
                continue;
            }
            if (head.length < 5 || !MAGIC.equals(head[0])) throw new IOException("bad response frame");
            var out = new String(readExactly(in, size(head[3])), StandardCharsets.UTF_8);
            var err = new String(readExactly(in, size(head[4])), StandardCharsets.UTF_8);
            return new Response(Integer.parseInt(head[1]), head[2], out, err);
        }
    }

    static int size(String s) throws IOException {
        var n = Integer.parseInt(s);
        if (n < 0 || n > MAX_FRAME) throw new IOException("frame length out of range: " + n);
        return n;
    }

    static String readLine(InputStream in) throws IOException {
        var buf = new ByteArrayOutputStream();
        for (var b = in.read(); b != '\n'; b = in.read()) {
            if (b < 0) throw new EOFException("connection closed before frame header");
            buf.write(b);
        }
        return buf.toString(StandardCharsets.UTF_8).trim();
    }

    static byte[] readExactly(InputStream in, int n) throws IOException {
        var buf = new byte[n];
        var read = 0;
        while (read < n) {
            var r = in.read(buf, read, n - read);
            if (r < 0) throw new EOFException("connection closed mid-frame");
            read += r;
        }
        return buf;
    }
}
