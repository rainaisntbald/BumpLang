package nativeClasses;

import bump.BumpException;
import bump.BumpClass;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class StringClass extends BumpClass {
    public StringClass(BumpClass runtimeClass, ObjectClass objectClass, BumpClass functionClass) {
        super(runtimeClass, "String", new HashMap<>(), createMethods(functionClass), objectClass, true);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("length", java.util.List.of(stringMethod(functionClass, "length", 0, self -> (interpreter, arguments) ->
                interpreter.wrapIntegerValue(self.length()))));
        methods.put("char_at", java.util.List.of(stringMethod(functionClass, "char_at", 1, self -> (interpreter, arguments) -> {
            int index = interpreter.requireIndexValue(arguments.get(0));
            if (index < 0 || index >= self.length()) {
                throw BumpRuntimeError.indexError("Index out of bounds: " + index);
            }
            return interpreter.wrapChar(self.charAt(index));
        })));
        methods.put("substring", java.util.List.of(stringMethod(functionClass, "substring", 2, self -> (interpreter, arguments) -> {
            int start = interpreter.requireIndexValue(arguments.get(0));
            int end = interpreter.requireIndexValue(arguments.get(1));
            if (start < 0 || end < start || end > self.length()) {
                throw BumpRuntimeError.indexError("Invalid substring range [" + start + ", " + end + ") for String of length " + self.length() + ".");
            }
            return interpreter.wrapString(self.value().substring(start, end));
        })));
        methods.put("slice", java.util.List.of(stringMethod(functionClass, "slice", 2, self -> (interpreter, arguments) -> {
            int start = interpreter.requireIndexValue(arguments.get(0));
            int length = interpreter.requireIndexValue(arguments.get(1));
            if (start < 0 || length < 0 || start + length > self.length()) {
                throw BumpRuntimeError.indexError("Invalid slice [" + start + ", " + length + "] for String of length " + self.length() + ".");
            }
            return interpreter.wrapString(self.value().substring(start, start + length));
        })));
        methods.put("index_of", java.util.List.of(stringMethod(functionClass, "index_of", 1, self -> (interpreter, arguments) -> {
            String needle = requireSearchText(arguments.get(0), "String.index_of()");
            return interpreter.wrapIntegerValue(self.value().indexOf(needle));
        })));
        methods.put("last_index_of", java.util.List.of(stringMethod(functionClass, "last_index_of", 1, self -> (interpreter, arguments) -> {
            String needle = requireSearchText(arguments.get(0), "String.last_index_of()");
            return interpreter.wrapIntegerValue(self.value().lastIndexOf(needle));
        })));
        methods.put("contains", java.util.List.of(stringMethod(functionClass, "contains", 1, self -> (interpreter, arguments) -> {
            String needle = requireSearchText(arguments.get(0), "String.contains()");
            return interpreter.wrapBooleanValue(self.value().contains(needle));
        })));
        methods.put("starts_with", java.util.List.of(stringMethod(functionClass, "starts_with", 1, self -> (interpreter, arguments) -> {
            String prefix = requireSearchText(arguments.get(0), "String.starts_with()");
            return interpreter.wrapBooleanValue(self.value().startsWith(prefix));
        })));
        methods.put("ends_with", java.util.List.of(stringMethod(functionClass, "ends_with", 1, self -> (interpreter, arguments) -> {
            String suffix = requireSearchText(arguments.get(0), "String.ends_with()");
            return interpreter.wrapBooleanValue(self.value().endsWith(suffix));
        })));
        methods.put("count", java.util.List.of(stringMethod(functionClass, "count", 1, self -> (interpreter, arguments) -> {
            String needle = requireSearchText(arguments.get(0), "String.count()");
            if (needle.isEmpty()) {
                return interpreter.wrapIntegerValue(self.length() + 1);
            }
            int count = 0;
            int index = 0;
            while (true) {
                index = self.value().indexOf(needle, index);
                if (index == -1) {
                    break;
                }
                count++;
                index += needle.length();
            }
            return interpreter.wrapIntegerValue(count);
        })));
        methods.put("trim", java.util.List.of(stringMethod(functionClass, "trim", 0, self -> (interpreter, arguments) ->
                interpreter.wrapString(self.value().trim()))));
        methods.put("lower", java.util.List.of(stringMethod(functionClass, "lower", 0, self -> (interpreter, arguments) ->
                interpreter.wrapString(self.value().toLowerCase()))));
        methods.put("upper", java.util.List.of(stringMethod(functionClass, "upper", 0, self -> (interpreter, arguments) ->
                interpreter.wrapString(self.value().toUpperCase()))));
        methods.put("replace", java.util.List.of(stringMethod(functionClass, "replace", 2, self -> (interpreter, arguments) -> {
            String target = requireSearchText(arguments.get(0), "String.replace()");
            String replacement = requireSearchText(arguments.get(1), "String.replace()");
            return interpreter.wrapString(self.value().replace(target, replacement));
        })));
        methods.put("repeat", java.util.List.of(stringMethod(functionClass, "repeat", 1, self -> (interpreter, arguments) -> {
            int count = interpreter.requireIndexValue(arguments.get(0));
            if (count < 0) {
                throw BumpRuntimeError.valueError("String.repeat() count must be non-negative.");
            }
            return interpreter.wrapString(self.value().repeat(count));
        })));
        methods.put("split", java.util.List.of(stringMethod(functionClass, "split", 1, self -> (interpreter, arguments) -> {
            String separator = requireSearchText(arguments.get(0), "String.split()");
            List<Object> parts = new ArrayList<>();
            if (separator.isEmpty()) {
                for (int i = 0; i < self.length(); i++) {
                    parts.add(interpreter.wrapChar(self.charAt(i)));
                }
                return interpreter.wrapArray(parts);
            }
            for (String part : self.value().split(Pattern.quote(separator), -1)) {
                parts.add(interpreter.wrapString(part));
            }
            return interpreter.wrapArray(parts);
        })));
        methods.put("_add", java.util.List.of(stringMethod(functionClass, "_add", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof StringInstance stringInstance) {
                return new StringInstance(self.getRuntimeClass(), self.value() + stringInstance.value());
            }
            if (arguments.get(0) instanceof CharInstance charInstance) {
                return new StringInstance(self.getRuntimeClass(), self.value() + charInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot concatenate String with " + BumpException.describeType(arguments.get(0)) + ".");
        })));
        methods.put("_eq", java.util.List.of(stringMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof StringInstance stringInstance) {
                return interpreter.wrapBooleanValue(self.value().equals(stringInstance.value()));
            }
            return interpreter.wrapBooleanValue(false);
        })));
        return methods;
    }

    private static String requireSearchText(Object value, String methodName) {
        if (value instanceof StringInstance stringInstance) {
            return stringInstance.value();
        }
        if (value instanceof CharInstance charInstance) {
            return Character.toString(charInstance.value());
        }
        if (value instanceof String string) {
            return string;
        }
        throw BumpRuntimeError.typeError(methodName + " expects a String or Char, but got " + BumpException.describeType(value) + ".");
    }

    private static NativeMethod stringMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<StringInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof StringInstance stringInstance)) {
                throw BumpException.internal("String method called on non-String instance.");
            }
            return binder.apply(stringInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof StringInstance stringInstance) {
            return new StringInstance(this, stringInstance.value());
        }
        if (value instanceof CharInstance charInstance) {
            return new StringInstance(this, Character.toString(charInstance.value()));
        }
        if (value instanceof String string) {
            return new StringInstance(this, string);
        }
        throw BumpRuntimeError.typeError("String() expects a String or Char value, but got " + BumpException.describeType(value) + ".");
    }
}
