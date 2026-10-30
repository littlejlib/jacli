package littlejlib.jacli.pack;

import module java.base;
import littlejlib.jacli.core.*;
import littlejlib.jacli.proj.FileStamp;

/// Everything a pack holds, as plain lists. The writer takes it, and {@link Pack#toData} gives it back,
/// which is how an incremental pass keeps the rows of files that did not change.
public record PackData(PackMeta meta, List<Decl> decls, List<Occurrence> refs, List<SuperEdge> supers,
                       List<FileStamp> stamps) { }
