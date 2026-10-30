package littlejlib.jacli.claude;

import module java.base;

/** One line per piece, and whether every piece is installed and current, or none is. */
record Status(List<String> lines, boolean current, boolean absent) { }
