package littlejlib.jacli.core;

public enum RefKind {
    CALL, NEW, METHOD_REF, TYPE, FIELD, EXTENDS, IMPLEMENTS, PERMITS, THROWS, ANNOTATION, IMPORT;

    public boolean isInvocation() { return this == CALL || this == NEW || this == METHOD_REF; }

    public boolean isSupertype() { return this == EXTENDS || this == IMPLEMENTS; }
}
