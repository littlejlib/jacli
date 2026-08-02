package fx;

import java.util.List;

public final class Shapes {

    private Shapes() { }

    /** The generic bound is a TYPE use of Shape, not an EXTENDS of it. */
    public static <T extends Shape> double totalArea(List<T> shapes) {
        var sum = 0d;
        for (var s : shapes) sum += s.area();
        return sum;
    }

    public static String render(Shape shape) {
        return shape.name() + ":" + shape.area();
    }

    public static String describeAll(List<Circle> circles) {
        var sb = new StringBuilder();
        for (var c : circles) sb.append(c.describe());
        return sb.toString();
    }

    /** Referenced by nothing, so `unused` must find it. */
    public static String orphan() {
        return "nobody calls me";
    }
}
