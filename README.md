# jacli — java assist cli

`jacli` (jack-lee) answers the questions grep has no form for, using the real javac compiler rather than string matching: who calls this, what implements this, where is this used, what does this method's body look like. It is a PATH binary, not a language server — invoked deliberately, needing no per-project install, and surviving anything that would kill an LSP session.

Maven and gradle are both first class. Which one a directory uses is detected, reported, and otherwise not your problem.

Built for coding agents. Default `jacli` with no arguments prints a full tutorial, because a CLI has no prompt attached the way an MCP server does — that help text is the only documentation an agent ever reads.

## Verbs

Three question shapes.

```
WHAT EXISTS?      jacli tree    [<pkg>]     packages and type names, cheapest possible look
                  jacli api     <scope>     what a type/package offers, with use counts
                                            --name <substr> searches declaration names

WHAT IS IT?       jacli def     <symbol>    declaration, signature, javadoc, use counts
                  jacli src     <symbol>    its source, sliced from the file

HOW IS IT USED?   jacli refs    <symbol>    every use, resolved
                  jacli callers <symbol>    who calls it, transitively with -d
                  jacli callees <symbol>    what it calls, transitively with -d
                  jacli impls   <type>      what extends or implements it, incl. indirectly
                  jacli unused              public API nothing in the project references

WHAT DOES IT NEED?  jacli deps    [-d N]      the closure, with each artifact's origin
                  jacli releasable          could anyone else build this project?

                  jacli index [--watch] | projects | status | daemon
```

`releasable` answers one question: why does this project build here and nowhere else. On maven, origins are read offline from the local repository's resolver markers — `central`, `remote:<id>`, `LOCAL-ONLY` (somebody ran `mvn install`), `MISSING` — and every blocker gets a mitigation. The walk is breadth-first so the version reported is the one maven's nearest-wins mediation would pick; optional, test and provided dependencies are excluded, and a pom-type dependency is checked for its pom rather than a jar it will never have. Profiles, `dependencyManagement` and version ranges are not expanded, so a clean verdict is good evidence rather than a guarantee.

On gradle the graph is better and the origins are worse, and both are stated. Better: when gradle can be run, the graph is its own resolution result, so the versions are the ones it would actually use rather than a pom walk's approximation. Worse: gradle records nowhere cheap to read *which* repository served an artifact, so a downloaded jar reads `cached`, and the verdict rests on the repositories the build declares — `mavenLocal()` and private `maven { url }` entries are named as the blockers they are.

Any verb can be pointed at a library instead of the current project with `-T`:

```
jacli tree -T io.github.luvml:luvs:2.0            maven coordinates from ~/.m2
jacli api  -T info.picocli:picocli:4.7.7 --name Option
jacli tree -T https://github.com/someone/somelib  shallow-cloned and cached
```

Best route first, always named in the output: a sources jar is extracted and parsed (real parameter names, line numbers, javadoc); failing that the jar's class files are read, which gives exact signatures and types but no line numbers and `arg0`-style parameter names. No decompiler — javac resolves signatures from bytecode directly. If neither jar is present, jacli prints the exact `mvn dependency:get` command. Targets are read on demand and never indexed, because nobody edits a dependency.

`api --name` is the discovery route: `jacli api --name flex` in `luvs` returns the twelve `flex*` declarations with their use counts and the three enum value types, answering "what does this library offer for flex" and "what are the legal values" in one call. It searches declaration names, so it never matches a comment or a string.

A symbol is `Type`, `pkg.Type`, `Type#member`, `Type#method(int,String)`, or the dotted `pkg.Type.method` form a stack trace prints. Ambiguous specs are never silently resolved: the candidates are listed and the exit code is 3.

## Why it beats grep on these codebases specifically

Our java guidelines mandate `var` for every local, module and wildcard imports, and static imports for enums and constants. Each of those deletes the token a textual search would have matched — `var count = 0` is invisible to a grep for `int count`, `joining(", ")` carries no `Collectors.` prefix, and `import module java.base` means a bare `List` never spells `java.util.List` anywhere in the file. The style that makes the code good to read makes it maximally hostile to string matching.

Beyond that, grep cannot separate a declaration from a call from a comment from a string literal, cannot choose between overloads, and cannot follow inheritance to indirect implementers. `jacli impls Out_I` finds `StringBuilderOut` through the abstract `Out_A` in between, which never spells the interface in its own file.

## Architecture

Three parts, for one reason each.

- **CLI** — thin. Forwards argv to the daemon and prints what comes back. Falls back to running in-process if there is no daemon.
- **Daemon** — keeps a warm javac file manager for the projects being edited (released after two idle minutes), watches their source roots, and answers from each project's pack. Requests are served one at a time under a lock because a `JavacTask` is not thread safe; a query itself only reads an immutable pack and costs milliseconds, with no javac involved.
- **Index** — one pack file per project in `~/littlejlib/jacli/packs/`, and `catalog.json` for the registry (which projects, their scope group, pins). A pack is a sorted string pool and flat integer tables for declarations, references, supertypes and file stamps, with sorted permutations so every lookup is a binary search over the bytes: no database, nothing built at load, about 50 bytes per reference. Every declaration and every reference occurrence is stored, because extracting all 13,067 references from a 488-file project costs 66 ms against the 11–30 seconds javac spends attributing the trees they came from. A change rewrites that project's whole pack beside the old one and renames it into place, so a project being rebuilt cannot disturb another, a crash leaves the previous index, and a reader holding the old bytes is never affected.
- **Build model** — one shape, `GradleModel`, filled either by gradle itself through a read-only init script or by reading the build files, so everything downstream is indifferent to which answered and only the reported source says. The ladder is the maven one: cache, tool offline, tool online, static read. It escalates on how much *resolved*, not on the exit code — per-project failures are caught inside the init script so one unresolvable subproject does not cost the whole model, which means gradle can exit 0 having quietly produced a classpath full of holes.

Modules always come from the build's own declaration — `<modules>`, or `include(...)` in the settings file — never from a directory walk. On teavm that is the difference between 2,991 files and 3,445: `html4j`, `samples/gamepad` and `tools/eclipse` are complete source trees the build no longer includes, and `tools/maven/webapp/src/main/resources/archetype-resources/` is a template containing a `pom.xml` and a `src/main/java` that any glob reads as a real project.

The engine is the javac API (`JavacTask`, `Trees`, `Elements`, `Types`) — public exported API in `jdk.compiler`, so no external dependency, real compiler semantics, and it parses JDK 25 `import module` natively where JavaParser cannot parse it at all.

## Measured

Machine state varies by about 2x between runs, so these are indicative rather than precise.

```
luvml   66 files    cold index 1.1-4s      warm query 0.8-1.0s
luvjfx  488 files   cold index 11-39s      warm query 1.1-1.4s
                    incremental after editing one file: +0.8s
```

On gradle the first call also has to configure the build, and if its dependency cache is cold that is a download. teavm, from nothing: 331 s to a complete model, most of it fetching ~4 GB, once. `JACLI_GRADLE_RUN=false` skips launching gradle entirely and reads the build files instead.

Against the stateless alternative, which re-analyses per call: `luvjfx` worst case measured 5.0s **per query**. The index pays for itself on the third question, and an agentic loop asks hundreds.

## Trust — read the tier

Every answer names what it is worth, because a degraded index presented as a clean one would turn "not resolved" into "no usages" and make jacli worse than grep rather than better.

- `RESOLVED` — javac attributed everything, no errors. Overloads and inheritance exact.
- `PARTIAL` — attributed with errors. Exact where it resolved; unresolved spellings marked `~`.
- `SYNTACTIC` — parse only. Same-named members of unrelated types are not distinguished. A structure-aware grep.
- `TEXTUAL` — would not parse. No better than grep.

Exit code 5 means nothing found *and* the index is degraded, which is not the same as exit 1. jacli deliberately still answers when a project does not compile — mid-refactor is exactly when you search hardest — and says how much the answer is worth.

A pack that cannot be read is deleted and rebuilt on that project's next query; nothing else is affected, because no pack shares a file with another.

## Tests

```
mvn test
```

77 tests, a few seconds, offline. Most exist because a field tester found the defect first: `api` on interfaces, `--overrides` tripping the ambiguity guard, package scopes parsed as member specs, a `--kind` filter claiming a symbol was unused, optional and pom-type dependencies reported as release blockers. They run against self-contained fixture projects with no dependencies, so javac resolves them against the platform alone, and no test is allowed to launch maven or gradle — they pin the engine's semantics rather than the state of this machine.

The gradle fixture is built out of the ways a real build defeats a naive reader: a commented-out `include`, a subproject directory the settings file no longer lists, coordinates that exist only in `libs.versions.toml`, a test-only dependency, an `includeBuild`, and a settings file whose includes are computed rather than written.

Every one of them has been checked to fail when its fix is reverted. A regression test that has never been seen to fail is a comment.

## Install

```
mvn package
copy C:\user\code\xyz-jphil\jr\jr.exe  C:\user\Apps\cmdtools\jacli.exe
```

`C:\user\Apps\cmdtools\jacli.jrc` points the launcher at `shade\littlejlib-jacli.jar` with the JDK 25 AOT cache on, which is what keeps a warm call under a second. Index, classpath cache and daemon state live in `~\littlejlib\jacli\`.

After rebuilding, nothing needs restarting by hand: the client sends the build stamp it expects and the daemon stops and restarts itself if it is running older code.

## Where grep is still right

Strings, comments, TODOs, log messages, poms, fxml, resources, markdown, config, anything that is not java. Also anything bound by name at runtime — reflection, dependency injection, service loaders — since a call site that exists only in a config file is invisible to a compiler and therefore to jacli. The two tools are complementary and the tutorial says so.

## Built with

javac API (JDK 25), picocli, jsoup for poms. Design mined from `georgewfraser/java-language-server` (MIT) — in particular its shortlist-then-resolve find-references path, and the constraint that an `Element` identity is only valid inside the `JavacTask` that produced it.
