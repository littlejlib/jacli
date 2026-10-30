package littlejlib.jacli.release;

import littlejlib.jacli.daemon.Build;
import picocli.CommandLine.IVersionProvider;

public final class Version implements IVersionProvider {

    public static String number() {
        return Build.STAMP.split(" ")[0];
    }

    @Override
    public String[] getVersion() {
        return new String[] { "jacli " + number() };
    }
}
