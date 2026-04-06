package nativeClasses;

import bump.BumpClass;

import java.util.HashMap;

public class ClassClass extends BumpClass {
    public ClassClass() {
        super(null, "Class", new HashMap<>(), new HashMap<>(), null, true);
    }

    public ClassClass(BumpClass runtimeClass, BumpClass superclass) {
        super(runtimeClass, "Class", new HashMap<>(), new HashMap<>(), superclass, true);
    }
}
