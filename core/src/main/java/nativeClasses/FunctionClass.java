package nativeClasses;

import bump.BumpClass;

import java.util.HashMap;

public class FunctionClass extends BumpClass {
    public FunctionClass(BumpClass runtimeClass, ObjectClass objectClass) {
        super(runtimeClass, "Function", new HashMap<>(), new HashMap<>(), objectClass, true);
    }
}
