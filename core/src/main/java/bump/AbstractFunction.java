package bump;

public abstract class AbstractFunction extends BumpInstance implements BumpCallable {
    public final String name;
    private final int arity;

    protected AbstractFunction(BumpClass runtimeClass, String name, int arity) {
        super(runtimeClass);
        this.name = name;
        this.arity = arity;
    }

    @Override
    public int arity() {
        return arity;
    }

    @Override
    public String toString() {
        if (name == null) {
            return "<fun>";
        }
        return "<fun " + name + ">";
    }
}
