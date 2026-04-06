package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;

public class ObjectInstance extends BumpInstance {
    public ObjectInstance(BumpClass runtimeClass) {
        super(runtimeClass);
    }

    @Override
    public String toString() {
        return runtimeClass.name;
    }
}
