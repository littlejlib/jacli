package littlejlib.jacli.pack;

/// Where things sit in a pack file. Every section is an array of big-endian ints, found through the
/// header's section table; the string bytes come last. All ids are string-pool ranks, and the pool is
/// sorted by its UTF-8 bytes so a lookup by spelling is a binary search with nothing built at load.
final class Layout {

    private Layout() { }

    static final int MAGIC = 0x4A504B31, VERSION = 1, HEADER = 48;

    /** Header ints before the section table. */
    static final int H_MAGIC = 0, H_VERSION = 1, H_TIER = 2, H_FILES = 3, H_DECLS = 4, H_REFS = 5, H_ERRORS = 6,
            H_UNRESOLVED = 7, H_FORMAT = 8, H_INDEX_MS = 9, H_INDEXED_AT = 11, H_COORDS = 13, H_ROOT = 14,
            H_CLASSPATH = 15, H_NOTES = 16, H_STR_BYTES = 17, H_SECTIONS = 20;

    /** Section numbers; each takes two header ints, offset (in ints) then length (in ints). */
    static final int STR_OFFSETS = 0, DECLS = 1, PARAMS = 2, DECL_BY_NAME = 3, DECL_BY_KEY = 4, DECL_BY_OWNER = 5,
            REFS = 6, REF_TARGETS = 7, REF_BY_ENCLOSING = 8, TARGET_NAMES = 9, SUPERS = 10, SUPER_BY_CHILD = 11,
            FILES = 12, SECTIONS = 13;

    /** Ints per row. */
    static final int DECL = 13, REF = 6, SUPER = 3, FILE = 5, IMPLICIT = 256;

    /** Fields of a declaration row. */
    static final int D_KEY = 0, D_KIND = 1, D_NAME = 2, D_OWNER = 3, D_FILE = 4, D_LINE = 5, D_COL = 6, D_END = 7,
            D_SIGNATURE = 8, D_MODIFIERS = 9, D_RETURN = 10, D_PARAMS = 11, D_DOC = 12;

    /** Fields of a reference row; its position is its rank by target. */
    static final int R_TARGET = 0, R_FILE = 1, R_LINE = 2, R_COL = 3, R_KIND = 4, R_ENCLOSING = 5;
}
