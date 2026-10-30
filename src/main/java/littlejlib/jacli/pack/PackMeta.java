package littlejlib.jacli.pack;

import littlejlib.jacli.core.Tier;

/// What a pack says about the run that made it: the totals and caveats a reader reports with an answer.
public record PackMeta(Tier tier, String root, String coords, String classpathSource, String notes, int files,
                       int declCount, int refCount, int javacErrors, int unresolved, int formatVersion,
                       long indexMs, long indexedAt) { }
