package littlejlib.jacli.proj;

import module java.base;

/** Runs maven as a child process. {@code mvn} on this machine is the AOT launcher, a drop-in for stock mvn. */
public final class Mvn {

    private Mvn() { }

    public static final int TIMEOUT_SECONDS = 240;

    public static boolean buildClasspath(Path projectDir, Path outFile, boolean offline, Consumer<String> log) {
        var args = new ArrayList<String>(List.of("mvn", "-q"));
        if (offline) args.add("-o");
        args.add("dependency:build-classpath");
        args.add("-Dmdep.outputFile=" + outFile.toAbsolutePath());
        args.add("-Dmdep.includeScope=compile");
        args.add("-Dmdep.pathSeparator=" + File.pathSeparator);
        var ok = Proc.run(projectDir, args, TIMEOUT_SECONDS, "mvn", log) && Files.isRegularFile(outFile);
        if (!ok) return false;
        try {
            return !Files.readString(outFile).isBlank();
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isWindows() { return Proc.isWindows(); }
}
