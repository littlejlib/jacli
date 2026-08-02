package littlejlib.jacli.core;

import module java.base;

/** Declarations and supertype links read from bytecode, with the type count actually seen in the jar. */
public record Read(List<Decl> decls, List<SuperEdge> supers, int typeCount) { }
