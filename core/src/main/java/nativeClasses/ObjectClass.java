package nativeClasses;

import bump.BumpClass;
import bump.BumpInstance;
import bump.BumpMethod;

import java.util.HashMap;
import java.util.Map;

public class ObjectClass extends BumpClass {
    public ObjectClass(BumpClass runtimeClass) {
        super(runtimeClass, "Object", new HashMap<>(), new HashMap<>(), null, true);
    }

    public void installMethods(BumpClass functionClass) {
        methods.put("_eq", java.util.List.of(objectMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> interpreter.wrapBooleanValue(self == arguments.get(0)))));
        methods.put("string", java.util.List.of(objectMethod(functionClass, "string", 0, self -> (interpreter, arguments) -> interpreter.wrapString(self.toString()))));
        methods.put("class_name", java.util.List.of(objectMethod(functionClass, "class_name", 0, self -> (interpreter, arguments) -> interpreter.wrapString(self.getRuntimeClass().name))));

        methods.put("_lt", java.util.List.of(objectMethod(functionClass, "_lt", 1, self -> (interpreter, arguments) -> {
            throw bump.BumpRuntimeError.typeError("Cannot compare " + self.getRuntimeClass().name + " - does not implement Comparable");
        })));
        methods.put("_lte", java.util.List.of(objectMethod(functionClass, "_lte", 1, self -> (interpreter, arguments) -> {
            throw bump.BumpRuntimeError.typeError("Cannot compare " + self.getRuntimeClass().name + " - does not implement Comparable");
        })));
        methods.put("_gt", java.util.List.of(objectMethod(functionClass, "_gt", 1, self -> (interpreter, arguments) -> {
            throw bump.BumpRuntimeError.typeError("Cannot compare " + self.getRuntimeClass().name + " - does not implement Comparable");
        })));
        methods.put("_gte", java.util.List.of(objectMethod(functionClass, "_gte", 1, self -> (interpreter, arguments) -> {
            throw bump.BumpRuntimeError.typeError("Cannot compare " + self.getRuntimeClass().name + " - does not implement Comparable");
        })));
    }

    private static NativeMethod objectMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<BumpInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, binder::apply);
    }
}
