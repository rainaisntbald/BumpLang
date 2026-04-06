package nativeClasses;

import bump.BumpException;
import bump.BumpClass;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BoolClass extends BumpClass {
    public BoolClass(BumpClass runtimeClass, ObjectClass objectClass, BumpClass functionClass) {
        super(runtimeClass, "Bool", new HashMap<>(), createMethods(functionClass), objectClass, true);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("_and", java.util.List.of(boolMethod(functionClass, "_and", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof BoolInstance boolInstance) {
                return new BoolInstance(self.getRuntimeClass(), self.value() && boolInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot apply && to Bool and " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_or", java.util.List.of(boolMethod(functionClass, "_or", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof BoolInstance boolInstance) {
                return new BoolInstance(self.getRuntimeClass(), self.value() || boolInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot apply || to Bool and " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_not", java.util.List.of(boolMethod(functionClass, "_not", 0, self -> (interpreter, arguments) ->
                new BoolInstance(self.getRuntimeClass(), !self.value()))));
        methods.put("_eq", java.util.List.of(boolMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof BoolInstance boolInstance) {
                return new BoolInstance(self.getRuntimeClass(), self.value() == boolInstance.value());
            }
            return new BoolInstance(self.getRuntimeClass(), false);
        })));
        return methods;
    }

    private static NativeMethod boolMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<BoolInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof BoolInstance boolInstance)) {
                throw BumpException.internal("Bool method called on non-Bool instance.");
            }
            return binder.apply(boolInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof BoolInstance boolInstance) {
            return new BoolInstance(this, boolInstance.value());
        }
        if (value instanceof Boolean bool) {
            return new BoolInstance(this, bool);
        }
        throw BumpRuntimeError.typeError("Bool() expects a Bool value, but got " + BumpException.describeType(value) + ".");
    }
}
