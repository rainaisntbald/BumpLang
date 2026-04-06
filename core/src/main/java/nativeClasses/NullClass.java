package nativeClasses;

import bump.BumpClass;

import java.util.HashMap;

public class NullClass extends BumpClass {
    public NullClass(BumpClass runtimeClass, ObjectClass objectClass) {
        super(runtimeClass, "Null", new HashMap<>(), new HashMap<>(), objectClass, true);
    }
}
