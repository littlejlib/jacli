<!-- Written by `jacli claude install`. `jacli claude uninstall` removes it, `jacli claude status` says whether it is current. -->
# jacli: Java code navigation

`jacli` is on PATH. It answers questions about Java SYMBOLS from the compiler's own resolution: what a type declares, who calls a method, what implements an interface, where something is declared, what its body is. In a Java project use it INSTEAD OF opening files to find out what is declared where, not in addition to reading them.

- `jacli tree`: packages and type names. Start here rather than globbing and opening files.
- `jacli impls <Type>`: every implementation, including indirect ones that never spell `implements`, which a text search misses.
- `jacli refs <Sym>` and `jacli callers <Type#method>`: every use and every caller, resolved to the right overload.
- `jacli api <Type>`, `jacli def <Sym>`, `jacli src <Type#member>`: what a type offers, what a member is, its body.
- `jacli at <file>:<line> [<file>:<line> ...]`: the declaration containing each line. Pass a whole stack trace in one call.
- `jacli api -T <groupId:artifactId:version> <Type>`: a dependency's API read straight from the local Maven or Gradle cache. Never hunt for a jar, unzip it or run javap on it.

Every verb takes several symbols in one call (`jacli def A B C`), and one call is cheaper than three. Read the tier in each answer: RESOLVED is trustworthy, and exit 5 means nothing was found AND the index is degraded, which is never proof that something is unused. The first call in a project compiles it and can take tens of seconds; later calls take well under a second. Keep text search for string literals, comments, poms and resources, and just read a file under ~150 lines. If the project does not compile, use grep. `jacli` alone prints a short help, `jacli <verb> --help` the detail.
