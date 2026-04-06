package nativeClasses;

import bump.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IntegerClass extends BumpClass {

    public IntegerClass(BumpClass runtimeClass, BumpClass superclass, List<BumpClass> interfaces, BumpClass functionClass) {
        super(runtimeClass, "Integer", new HashMap<>(), new HashMap<>(), createMethods(functionClass), superclass, interfaces, null, true, false);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("_add", java.util.List.of(integerMethod(functionClass, "_add", 1, self -> (interpreter, arguments) -> {
            if(arguments.get(0) instanceof IntegerInstance integerInstance) {
                return new IntegerInstance(self.getRuntimeClass(), self.value() + integerInstance.value());
            }
            if(arguments.get(0) instanceof FloatInstance floatInstance) {
                return new FloatInstance(interpreter.floatClass(), self.value() + floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot add " + BumpException.describeType(arguments.get(0)) + " to Integer.");
        })));
        methods.put("_sub", java.util.List.of(integerMethod(functionClass, "_sub", 1, self -> (interpreter, arguments) -> {
            if(arguments.get(0) instanceof IntegerInstance integerInstance) {
                return new IntegerInstance(self.getRuntimeClass(), self.value() - integerInstance.value());
            }
            if(arguments.get(0) instanceof FloatInstance floatInstance) {
                return new FloatInstance(interpreter.floatClass(), self.value() - floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot subtract " + BumpException.describeType(arguments.get(0)) + " from Integer.");
        })));
        methods.put("_div", java.util.List.of(integerMethod(functionClass, "_div", 1, self -> (interpreter, arguments) -> {
            if(arguments.get(0) instanceof IntegerInstance integerInstance) {
                if(integerInstance.value() == 0) throw BumpRuntimeError.divisionByZero("Cannot divide by zero.");
                return new IntegerInstance(self.getRuntimeClass(), self.value() / integerInstance.value());
            }
            if(arguments.get(0) instanceof FloatInstance floatInstance) {
                if(floatInstance.value() == 0.0d) throw BumpRuntimeError.divisionByZero("Cannot divide by zero.");
                return new FloatInstance(interpreter.floatClass(), self.value() / floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot divide Integer by " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_mul", java.util.List.of(integerMethod(functionClass, "_mul", 1, self -> (interpreter, arguments) -> {
            if(arguments.get(0) instanceof IntegerInstance integerInstance) {
                return new IntegerInstance(self.getRuntimeClass(), self.value() * integerInstance.value());
            }
            if(arguments.get(0) instanceof FloatInstance floatInstance) {
                return new FloatInstance(interpreter.floatClass(), self.value() * floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot multiply Integer by " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_mod", java.util.List.of(integerMethod(functionClass, "_mod", 1, self -> (interpreter, arguments) -> {
            if(arguments.get(0) instanceof IntegerInstance integerInstance) {
                if(integerInstance.value() == 0) throw BumpRuntimeError.divisionByZero("Cannot divide by zero.");
                return new IntegerInstance(self.getRuntimeClass(), self.value() % integerInstance.value());
            }
            if(arguments.get(0) instanceof FloatInstance floatInstance) {
                if(floatInstance.value() == 0.0d) throw BumpRuntimeError.divisionByZero("Cannot divide by zero.");
                return new FloatInstance(interpreter.floatClass(), self.value() % floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot modulo Integer by " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_neg", java.util.List.of(integerMethod(functionClass, "_neg", 0, self -> (interpreter, arguments) -> new IntegerInstance(self.getRuntimeClass(), -self.value()))));
        methods.put("_eq", java.util.List.of(integerMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return interpreter.wrapBooleanValue(self.value().equals(integerInstance.value()));
            }
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return interpreter.wrapBooleanValue(self.value() == floatInstance.value());
            }
            return interpreter.wrapBooleanValue(false);
        })));
        methods.put("_gt", java.util.List.of(integerMethod(functionClass, "_gt", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return interpreter.wrapBooleanValue(self.value() > integerInstance.value());
            }
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return interpreter.wrapBooleanValue(self.value() > floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot compare Integer to " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_gte", java.util.List.of(integerMethod(functionClass, "_gte", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return interpreter.wrapBooleanValue(self.value() >= integerInstance.value());
            }
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return interpreter.wrapBooleanValue(self.value() >= floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot compare Integer to " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_lt", java.util.List.of(integerMethod(functionClass, "_lt", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return interpreter.wrapBooleanValue(self.value() < integerInstance.value());
            }
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return interpreter.wrapBooleanValue(self.value() < floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot compare Integer to " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_lte", java.util.List.of(integerMethod(functionClass, "_lte", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return interpreter.wrapBooleanValue(self.value() <= integerInstance.value());
            }
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return interpreter.wrapBooleanValue(self.value() <= floatInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot compare Integer to " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        return methods;
    }

    private static NativeMethod integerMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<IntegerInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if(!(instance instanceof IntegerInstance integerInstance)) {
                throw BumpException.internal("Integer method called on non-Integer instance.");
            }
            return binder.apply(integerInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if(value instanceof IntegerInstance integerInstance) {
            return new IntegerInstance(this, integerInstance.value());
        } if(value instanceof Integer integer) {
            return new IntegerInstance(this, integer);
        }
        throw BumpRuntimeError.typeError("Integer() expects an Integer value, but got " + BumpException.describeType(value) + ".");
    }
}
