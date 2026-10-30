package littlejlib.jacli.pack;

import module java.base;

/// A growing int array, so the writer never boxes a million ids.
final class Ints {

    int[] a = new int[256];
    int n;

    Ints add(int v) {
        if (n == a.length) a = Arrays.copyOf(a, n * 2);
        a[n++] = v;
        return this;
    }

    int[] toArray() { return Arrays.copyOf(a, n); }
}
