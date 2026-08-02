package littlejlib.jacli.daemon;

import module java.base;

public record Request(String expectedStamp, String cwd, List<String> args) {

    public boolean wantsStamp() { return !"-".equals(expectedStamp); }

    public boolean stampMatches(String actual) {
        return !wantsStamp() || expectedStamp.equals(actual.replace(' ', '_'));
    }
}
