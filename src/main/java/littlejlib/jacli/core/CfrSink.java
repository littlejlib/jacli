package littlejlib.jacli.core;

import module java.base;
import org.benf.cfr.reader.api.OutputSinkFactory;

/**
 * Collects CFR's decompiled output in memory instead of letting it write files.
 * <p>
 * CFR's default behaviour is to write a source tree to disk; a sink keeps the result where it is
 * wanted and avoids inventing an output directory only to read it straight back.
 */
public final class CfrSink implements OutputSinkFactory {

    final StringBuilder java = new StringBuilder();
    final List<String> problems = new ArrayList<>();

    public String java() { return java.toString(); }

    public List<String> problems() { return problems; }

    /**
     * STRING only, deliberately. The DECOMPILED sink class hands back a structured object whose
     * {@code toString} is the object, not the source — asking for it produced a line reading
     * {@code SinkDumperFactory$3$1@31f6ecd3} instead of java.
     */
    @Override
    public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
        return List.of(SinkClass.STRING);
    }

    @Override
    public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
        return switch (sinkType) {
            case JAVA -> value -> java.append(value);
            case EXCEPTION -> value -> problems.add(String.valueOf(value));
            default -> value -> { };
        };
    }
}
