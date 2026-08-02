package fx;

/** Reaches Shape only through AbstractShape, and never spells Shape in its implements clause. */
public final class Circle extends AbstractShape {

    final double radius;

    public Circle(double radius) {
        super("circle");
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public Shape scaled(double factor) {
        return new Circle(radius * factor);
    }
}
