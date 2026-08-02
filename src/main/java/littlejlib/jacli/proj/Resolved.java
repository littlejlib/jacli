package littlejlib.jacli.proj;

import module java.base;

/** A classpath plus how it was obtained, so a degraded answer can say why it is degraded. */
public record Resolved(String classpath, String source, boolean hasCompiledOutput, List<String> notes) {

    public int entryCount() {
        return classpath == null || classpath.isBlank() ? 0 : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator)).length;
    }
}
