---
name: java-code-nav
description: Use BEFORE reading or searching Java code, for ANY question about Java types, methods or fields. Orienting in an unfamiliar codebase - what exists, what the extension point is, what implements or extends an interface. Who calls this, what overrides it, every use of it before a rename or signature change. A stack-trace frame, compiler error, or any file:line. The API or source of a Maven/Gradle dependency you have not opened. `jacli` answers from the compiler's own resolution, so it finds implementations that never spell `implements` and uses that a text search cannot see.
---

# Java code navigation with `jacli`

`jacli` is on PATH. It resolves the project with its real build, compiles it once, and answers from the compiler's symbol table — so it separates a call from a declaration from a word in a comment, picks the right overload, and sees an implementation that never spells `implements`.

## Use it INSTEAD OF reading, not as well

**Measured: getting jacli used alongside file reads costs MORE than not using it at all.** The gain only appears when a jacli call replaces a Read, not when it precedes one. So do not open a `.java` file merely to find what is declared in it - that is the thing these verbs replace. Open a file when you need its literal body text for something jacli has already located.

## Spend round trips, not payload

**The cost of an answer is the number of tool calls it took, not the size of what came back.** Every extra call re-sends the whole conversation. One `jacli` call returning 40 lines is far cheaper than three returning 5 each, and reading one 1500-line file in a single call can beat six precise little queries. So:

- **Budget two to four calls for a whole question.** If you are on your sixth, you are exploring, not diagnosing.
- **Batch.** Every verb takes several symbols and shares one index load: `jacli src 'A#x' 'B#y' 'C#z'` — never three separate calls. Same for `def`, `refs`, `callers`, `impls`, `api`, `at`.
- **Do not open with `jacli status`.** Just ask your real question; the first call reports its own index state in the header.
- Ask the widest useful question first, then narrow once. Do not walk the graph node by node.

## Never claim something is unused, unique or "the only one" without asking

**This is the claim most often got wrong, and it is one call to check.** Before you write that a method is unused API, that nothing guards against a case, or that one class is the only one doing something — run `refs` on it and count:

    jacli refs 'Session#isOpen()'      8 references in 8 files - so it is NOT unused

A symbol query tells you about the symbol you asked about. It says nothing about the symbols you did not ask about, and the confident sentence you are about to write is usually about those. If the claim matters to your conclusion, spend the call.

## Recipes

**A stack trace or a compiler error** — you have `Dispatcher.java:842`:

    jacli at src/main/java/com/example/net/Dispatcher.java:842

It reports the declaration containing that line, its exact span, and every symbol referenced there — so you never open the file to find out which method a line belongs to.

**A stack trace is SEVERAL positions. Pass them all to one call; never walk the frames one at a time:**

    jacli at Dispatcher.java:842 Router.java:118 Server.java:64 Main.java:31

One round trip resolves every frame to its enclosing method and the symbols each touches, before any file is opened. The outer frames are usually where the thing was wired up, and this is how you see them without spending a query on each.

If a value was null, ask what writes it — the FIELD, not the accessor:

    jacli refs 'com.example.net.Session#channel' --field

Every read and every write in one answer. Then, if a writer looks conditional, ask who reaches it:

    jacli callers 'com.example.net.Session#bindChannel' -d 2

A caller printed with nothing above it has no callers — that is dead code, and it is the distinction a grep for the method name cannot make.

**One of a family misbehaves** — one codec, one handler, one implementation of nine. Compare it against its siblings in a single call rather than inspecting it alone; the defect is usually the thing the other eight do and this one does not:

    jacli src 'JsonCodec#decode' 'XmlCodec#decode' 'CsvCodec#decode'

Symbol queries answer what a thing IS and who touches it. They do not, on their own, show you that this one member breaks a pattern the rest keep — so when a family exists, ask for the family.

**Before a rename or a signature change:**

    jacli refs 'Poster#shutdown'      every use, resolved, right overload
    jacli callers 'Poster#shutdown'   who calls it
    jacli impls Poster                what implements it, INCLUDING indirectly

**Orienting in code you have not seen:**

    jacli tree                    packages and type names
    jacli api <Type> --inherited  everything callable on it, supertypes folded in
    jacli api --name 'post|send'  alternation, glob (get*Name), ~regex

**A dependency — a jar in ~/.m2 or the gradle cache, your own artifacts included:**

    jacli tree -T io.github.luvml:luvs:2.0
    jacli api  -T info.picocli:picocli:4.7.7 --name Option
    jacli src  -T com.arcadedb:arcadedb-engine:26.7.3 'com.arcadedb.database.DatabaseFactory#open'

Never hunt the jar by hand and never unpack it. `-T` reads the sources jar if present, decompiles a body on request, and labels it.

## Do NOT use it for

- Non-Java files, build logs, config, poms, markdown, string literals, comments. Those are text — use grep.
- Code that does not compile. Mid-refactor, grep is the right tool.
- `javap -v`: constant pool and raw bytecode. jacli does not do that.
- A file you genuinely need end to end. Read it.
- Editing. jacli is read-only — it plans a refactor, you perform it with Edit.

## Reading the result

Exit codes: `0` found, `1` nothing found, `2` usage, `3` ambiguous — the candidates are listed with the flag to disambiguate (`--field`, `--method`, `--all`, or a full signature), `4` not indexed, **`5` nothing found but the index is degraded, so absence is NOT established** — never report "no usages" on a 5.

The **first** call in a project compiles it: tens of seconds on a large one, with progress on stderr. Do not kill it and fall back to grep — every later call is ~0.3s and that is where the whole gain is.

Symbols: `Type` | `pkg.Type` | `Type#member` | `'Type#method(int,String)'` | `pkg.Type.method` (stack-trace form).

`jacli` bare prints a one-screen brief; `jacli <verb> --help` documents one verb.
