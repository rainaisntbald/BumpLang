package bump;

public abstract class NativeFunction extends AbstractFunction {
    protected NativeFunction(BumpClass runtimeClass, String name, int arity) {
        super(runtimeClass, name, arity);
    }
}
