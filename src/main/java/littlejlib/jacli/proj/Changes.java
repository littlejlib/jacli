package littlejlib.jacli.proj;

import module java.base;

public record Changes(List<String> changed, List<String> removed) {

    public boolean isEmpty() { return changed.isEmpty() && removed.isEmpty(); }

    public int size() { return changed.size() + removed.size(); }

    @Override
    public String toString() { return changed.size() + " changed, " + removed.size() + " removed"; }
}
