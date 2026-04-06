package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

public class FloatInstance extends BumpInstance {
    private final double value;

    public FloatInstance(BumpClass runtimeClass, double value) {
        super(runtimeClass);
        this.value = value;
    }

    public double value() {
        return value;
    }

    @Override
    public String toString() {
        return Double.toString(value);
    }
}
