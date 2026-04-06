package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

public class IntegerInstance extends BumpInstance {
    private final Integer value;

    public IntegerInstance(BumpClass runtimeClass, Integer value) {
        super(runtimeClass);
        this.value = value;
    }

    public Integer value() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
