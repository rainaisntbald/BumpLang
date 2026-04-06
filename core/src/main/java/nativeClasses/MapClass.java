package nativeClasses;

import bump.BumpClass;
import bump.BumpException;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapClass extends BumpClass {
    public MapClass(BumpClass runtimeClass, ObjectClass objectClass, BumpClass functionClass) {
        super(runtimeClass, "Map", new HashMap<>(), createMethods(functionClass), objectClass, true);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("length", java.util.List.of(mapMethod(functionClass, "length", 0, self -> (interpreter, arguments) ->
                interpreter.wrapIntegerValue(self.keys().size()))));
        methods.put("contains_key", java.util.List.of(mapMethod(functionClass, "contains_key", 1, self -> (interpreter, arguments) ->
                interpreter.wrapBooleanValue(indexOfKey(interpreter, self, arguments.get(0)) != -1))));
        methods.put("get", java.util.List.of(mapMethod(functionClass, "get", 1, self -> (interpreter, arguments) -> {
            int index = indexOfKey(interpreter, self, arguments.get(0));
            if (index == -1) {
                throw BumpRuntimeError.propertyError("Map key not found: " + arguments.get(0));
            }
            return self.values().get(index);
        })));
        methods.put("set", java.util.List.of(mapMethod(functionClass, "set", 2, self -> (interpreter, arguments) -> {
            int index = indexOfKey(interpreter, self, arguments.get(0));
            Object value = arguments.get(1);
            if (index == -1) {
                self.keys().add(arguments.get(0));
                self.values().add(value);
            } else {
                self.values().set(index, value);
            }
            return value;
        })));
        methods.put("remove", java.util.List.of(mapMethod(functionClass, "remove", 1, self -> (interpreter, arguments) -> {
            int index = indexOfKey(interpreter, self, arguments.get(0));
            if (index == -1) {
                throw BumpRuntimeError.propertyError("Map key not found: " + arguments.get(0));
            }
            self.keys().remove(index);
            return self.values().remove(index);
        })));
        methods.put("keys", java.util.List.of(mapMethod(functionClass, "keys", 0, self -> (interpreter, arguments) ->
                interpreter.wrapArray(self.keys()))));
        methods.put("values", java.util.List.of(mapMethod(functionClass, "values", 0, self -> (interpreter, arguments) ->
                interpreter.wrapArray(self.values()))));
        methods.put("clear", java.util.List.of(mapMethod(functionClass, "clear", 0, self -> (interpreter, arguments) -> {
            self.keys().clear();
            self.values().clear();
            return interpreter.wrapNull();
        })));
        methods.put("copy", java.util.List.of(mapMethod(functionClass, "copy", 0, self -> (interpreter, arguments) ->
                new MapInstance(self.getRuntimeClass(), self.keys(), self.values()))));
        methods.put("_eq", java.util.List.of(mapMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            if (!(arguments.get(0) instanceof MapInstance other)) {
                return interpreter.wrapBooleanValue(false);
            }
            if (self.keys().size() != other.keys().size()) {
                return interpreter.wrapBooleanValue(false);
            }
            for (int i = 0; i < self.keys().size(); i++) {
                int otherIndex = indexOfKey(interpreter, other, self.keys().get(i));
                if (otherIndex == -1) {
                    return interpreter.wrapBooleanValue(false);
                }
                if (!interpreter.valuesEqual(self.values().get(i), other.values().get(otherIndex))) {
                    return interpreter.wrapBooleanValue(false);
                }
            }
            return interpreter.wrapBooleanValue(true);
        })));
        return methods;
    }

    private static int indexOfKey(Interpreter interpreter, MapInstance self, Object key) {
        for (int i = 0; i < self.keys().size(); i++) {
            if (interpreter.valuesEqual(self.keys().get(i), key)) {
                return i;
            }
        }
        return -1;
    }

    private static NativeMethod mapMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<MapInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof MapInstance mapInstance)) {
                throw BumpException.internal("Map method called on non-Map instance.");
            }
            return binder.apply(mapInstance);
        });
    }

    @Override
    public int arity() {
        return 0;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        return new MapInstance(this);
    }
}
