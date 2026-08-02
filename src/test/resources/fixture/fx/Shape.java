package fx;

/** A shape. Interface members are implicitly public, which is the case that broke api. */
public interface Shape {

    String name();

    double area();

    Shape scaled(double factor);

    Shape scaled(int factor);
}
