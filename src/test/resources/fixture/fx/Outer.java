package fx;

public final class Outer {

    /** Components on their own lines, so an accessor's position is distinguishable from the header's. */
    public record Point(
            int x,
            int y) {

        public int sum() {
            return x + y;
        }
    }

    public static final class Inner implements Shape {

        @Override public String name() { return "inner"; }

        @Override public double area() { return 0; }

        @Override public Shape scaled(double factor) { return this; }

        @Override public Shape scaled(int factor) { return this; }
    }

    public static Point origin() {
        return new Point(0, 0);
    }

    /** Calls the record's IMPLICIT accessor. A real call site that must not be reported as no use. */
    public static int firstCoordinate(Point p) {
        return p.x();
    }
}
