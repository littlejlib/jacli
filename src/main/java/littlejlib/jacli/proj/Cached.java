package littlejlib.jacli.proj;

/** A cached classpath and the step that originally produced it, so a degraded source stays visible. */
record Cached(String classpath, String source) { }
