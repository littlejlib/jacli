package littlejlib.jacli.core;

import javax.lang.model.element.ElementKind;

public enum DeclKind {
    CLASS, INTERFACE, ENUM, RECORD, ANNOTATION, METHOD, CONSTRUCTOR, FIELD, ENUM_CONSTANT, OTHER;

    public boolean isType() {
        return switch (this) {
            case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION -> true;
            default -> false;
        };
    }

    public boolean isExecutable() { return this == METHOD || this == CONSTRUCTOR; }

    public static DeclKind of(ElementKind k) {
        return switch (k) {
            case CLASS -> CLASS;
            case INTERFACE -> INTERFACE;
            case ENUM -> ENUM;
            case RECORD -> RECORD;
            case ANNOTATION_TYPE -> ANNOTATION;
            case METHOD -> METHOD;
            case CONSTRUCTOR -> CONSTRUCTOR;
            case FIELD -> FIELD;
            case ENUM_CONSTANT -> ENUM_CONSTANT;
            default -> OTHER;
        };
    }
}
