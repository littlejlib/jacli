package littlejlib.jacli.core;

import module java.base;
import module jdk.compiler;
import javax.lang.model.element.Modifier;
import javax.tools.ToolProvider;

/**
 * API surface read from a jar's class files, for a dependency that ships no sources.
 * <p>
 * No decompiler. javac resolves signatures from bytecode directly, so signatures here are exact
 * rather than reconstructed — a decompiler in the middle would add a dependency, a slow step, and a
 * chance to be wrong. What bytecode cannot give is source positions, so these declarations carry the
 * jar as their file and no line, and {@code src} cannot work on them.
 */
public final class ClassApi {

    private ClassApi() { }

    public static Read read(Path jar, boolean includeNonPublic, Consumer<String> log) {
        var names = typeNames(jar);
        log.accept(names.size() + " top-level types in " + jar.getFileName());
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diags = new DiagnosticCollector<JavaFileObject>();
        var fm = compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8);
        var options = List.of("-classpath", jar.toString(), "-proc:none", "-nowarn");
        var task = (JavacTask) compiler.getTask(null, fm, diags, options, null, List.of(StringSource.probe()));
        var decls = new ArrayList<Decl>();
        var supers = new ArrayList<SuperEdge>();
        try {
            task.parse();
            task.analyze();
            var elements = task.getElements();
            var types = task.getTypes();
            var file = jar.getFileName().toString();
            for (var name : names) {
                var te = elements.getTypeElement(name);
                if (te == null) continue;
                collect(te, types, file, includeNonPublic, decls, supers);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Read(List.copyOf(decls), List.copyOf(supers), names.size());
    }

    static void collect(TypeElement te, Types types, String file, boolean includeNonPublic,
                        List<Decl> decls, List<SuperEdge> supers) {
        if (!visible(te, includeNonPublic)) return;
        var key = SymbolKey.of(te, types);
        if (key == null) return;
        decls.add(decl(te, key, ownerKey(te, types), file, types));
        for (var s : types.directSupertypes(te.asType()))
            if (types.asElement(s) instanceof TypeElement st
                    && !st.getQualifiedName().contentEquals("java.lang.Object"))
                supers.add(new SuperEdge(key, SymbolKey.of(st, types), true));
        for (var member : te.getEnclosedElements()) {
            if (member instanceof TypeElement nested) {
                collect(nested, types, file, includeNonPublic, decls, supers);
                continue;
            }
            if (!visible(member, includeNonPublic)) continue;
            var mk = SymbolKey.of(member, types);
            if (mk != null) decls.add(decl(member, mk, key, file, types));
        }
    }

    static Decl decl(Element el, String key, String ownerKey, String file, Types types) {
        var ret = el instanceof ExecutableElement m && m.getKind() != ElementKind.CONSTRUCTOR
                ? SymbolKey.erased(m.getReturnType(), types)
                : el instanceof VariableElement f ? SymbolKey.erased(f.asType(), types) : null;
        var params = el instanceof ExecutableElement m
                ? m.getParameters().stream().map(p -> SymbolKey.erased(p.asType(), types)).toList()
                : List.<String>of();
        return new Decl(key, DeclKind.of(el.getKind()), el.getSimpleName().toString(), ownerKey, file,
                0, 0, 0, SymbolKey.signature(el, types), SymbolKey.modifiers(el), ret, params, List.of(), null);
    }

    static String ownerKey(TypeElement te, Types types) {
        return te.getEnclosingElement() instanceof TypeElement outer ? SymbolKey.of(outer, types) : null;
    }

    static boolean visible(Element el, boolean includeNonPublic) {
        return includeNonPublic || el.getModifiers().contains(Modifier.PUBLIC)
                || el.getModifiers().contains(Modifier.PROTECTED);
    }

    /** Top-level types only; nested ones are reached through {@code getEnclosedElements}. */
    static List<String> typeNames(Path jar) {
        var out = new ArrayList<String>();
        try (var zf = new java.util.zip.ZipFile(jar.toFile())) {
            for (var it = zf.entries(); it.hasMoreElements(); ) {
                var name = it.nextElement().getName();
                if (!name.endsWith(".class") || name.contains("$")) continue;
                if (name.endsWith("module-info.class") || name.endsWith("package-info.class")) continue;
                if (name.startsWith("META-INF/")) continue;
                out.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Collections.sort(out);
        return out;
    }
}
