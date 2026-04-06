package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

public class NullInstance extends BumpInstance {
    public NullInstance(BumpClass runtimeClass) {
        super(runtimeClass);
    }

    @Override
    public String toString() {
        return "null";
    }
}
