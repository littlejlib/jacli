package fx;

/** Implements Shape directly, so Circle below reaches Shape only indirectly, through this. */
public abstract class AbstractShape implements Shape {

    protected final String label;

    protected AbstractShape(String label) {
        this.label = label;
    }

    @Override
    public String name() {
        return label;
    }

    @Override
    public Shape scaled(int factor) {
        return scaled((double) factor);
    }

    public String describe() {
        return name() + " area=" + area();
    }
}
