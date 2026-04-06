package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

public class BoolInstance extends BumpInstance {
    private final boolean value;

    public BoolInstance(BumpClass runtimeClass, boolean value) {
        super(runtimeClass);
        this.value = value;
    }

    public boolean value() {
        return value;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
