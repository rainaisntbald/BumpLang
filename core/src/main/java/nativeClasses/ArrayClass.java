package nativeClasses;

import bump.BumpException;
import bump.BumpClass;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArrayClass extends BumpClass {
    public ArrayClass(BumpClass runtimeClass, ObjectClass objectClass, BumpClass functionClass) {
        super(runtimeClass, "Array", new HashMap<>(), createMethods(functionClass), objectClass, true);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("length", java.util.List.of(arrayMethod(functionClass, "length", 0, self -> (interpreter, arguments) -> interpreter.wrapIntegerValue(self.values().size()))));
        methods.put("get", java.util.List.of(arrayMethod(functionClass, "get", 1, self -> (interpreter, arguments) -> {
            Integer index = interpreter.requireIndexValue(arguments.get(0));
            if(self.values().size() <= index || index < 0) {
                throw BumpRuntimeError.indexError("Index out of bounds: " + index);
            }
            return self.values().get(interpreter.requireIndexValue(arguments.get(0)));
        })));
        methods.put("set", java.util.List.of(arrayMethod(functionClass, "set", 2, self -> (interpreter, arguments) -> {
            int index = interpreter.requireIndexValue(arguments.get(0));
            if(self.values().size() <= index || index < 0) {
                throw BumpRuntimeError.indexError("Index out of bounds: " + index);
            }
            Object value = arguments.get(1);
            self.values().set(index, value);
            return value;
        })));
        methods.put("push", java.util.List.of(arrayMethod(functionClass, "push", 1, self -> (interpreter, arguments) -> {
            Object value = arguments.get(0);
            self.values().add(value);
            return value;
        })));
        methods.put("pop", java.util.List.of(arrayMethod(functionClass, "pop", 0, self -> (interpreter, arguments) -> {
            if (self.values().isEmpty()) {
                throw BumpRuntimeError.indexError("Cannot pop from an empty array.");
            }
            return self.values().remove(self.values().size() - 1);
        })));
        methods.put("insert", java.util.List.of(arrayMethod(functionClass, "insert", 2, self -> (interpreter, arguments) -> {
            int index = interpreter.requireIndexValue(arguments.get(0));
            if (index < 0 || index > self.values().size()) {
                throw BumpRuntimeError.indexError("Index out of bounds: " + index);
            }
            Object value = arguments.get(1);
            self.values().add(index, value);
            return value;
        })));
        methods.put("remove", java.util.List.of(arrayMethod(functionClass, "remove", 1, self -> (interpreter, arguments) -> {
            int index = interpreter.requireIndexValue(arguments.get(0));
            if (self.values().size() <= index || index < 0) {
                throw BumpRuntimeError.indexError("Index out of bounds: " + index);
            }
            return self.values().remove(index);
        })));
        methods.put("clear", java.util.List.of(arrayMethod(functionClass, "clear", 0, self -> (interpreter, arguments) -> {
            self.values().clear();
            return interpreter.wrapNull();
        })));
        methods.put("copy", java.util.List.of(arrayMethod(functionClass, "copy", 0, self -> (interpreter, arguments) ->
                interpreter.wrapArray(self.values()))));
        methods.put("contains", java.util.List.of(arrayMethod(functionClass, "contains", 1, self -> (interpreter, arguments) -> {
            Object needle = arguments.get(0);
            for (Object value : self.values()) {
                if (interpreter.valuesEqual(value, needle)) {
                    return interpreter.wrapBooleanValue(true);
                }
            }
            return interpreter.wrapBooleanValue(false);
        })));
        methods.put("index_of", java.util.List.of(arrayMethod(functionClass, "index_of", 1, self -> (interpreter, arguments) -> {
            Object needle = arguments.get(0);
            for (int i = 0; i < self.values().size(); i++) {
                if (interpreter.valuesEqual(self.values().get(i), needle)) {
                    return interpreter.wrapIntegerValue(i);
                }
            }
            return interpreter.wrapIntegerValue(-1);
        })));
        methods.put("join", java.util.List.of(arrayMethod(functionClass, "join", 1, self -> (interpreter, arguments) -> {
            Object rawSeparator = arguments.get(0);
            String separator;
            if (rawSeparator instanceof StringInstance stringInstance) {
                separator = stringInstance.value();
            } else if (rawSeparator instanceof String string) {
                separator = string;
            } else {
                throw BumpRuntimeError.typeError("Array.join() expects a String separator, but got " + BumpException.describeType(rawSeparator) + ".");
            }

            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < self.values().size(); i++) {
                if (i > 0) {
                    joined.append(separator);
                }
                joined.append(self.values().get(i));
            }
            return interpreter.wrapString(joined.toString());
        })));
        methods.put("_eq", java.util.List.of(arrayMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            Object value = arguments.get(0);
            if(!(value instanceof ArrayInstance other)) return interpreter.wrapBooleanValue(false);
            if(self.values().size() != other.values().size()) return interpreter.wrapBooleanValue(false);
            for(int i = 0; i < self.values().size(); i++) {
                if(!interpreter.valuesEqual(self.values().get(i), other.values().get(i))) return interpreter.wrapBooleanValue(false);
            }
            return interpreter.wrapBooleanValue(true);
        })));
        return methods;
    }

    private static NativeMethod arrayMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<ArrayInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof ArrayInstance arrayInstance)) {
                throw BumpException.internal("Array method called on non-array instance.");
            }
            return binder.apply(arrayInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof ArrayInstance arrayInstance) {
            return new ArrayInstance(this, arrayInstance.values());
        }
        if (value instanceof List<?> list) {
            return new ArrayInstance(this, new ArrayList<>(list));
        }
        throw BumpRuntimeError.typeError("Array() expects an Array value, but got " + BumpException.describeType(value) + ".");
    }
}
