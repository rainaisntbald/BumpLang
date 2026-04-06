package nativeClasses;

import bump.BumpClass;
import bump.BumpException;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CharClass extends BumpClass {
    public CharClass(BumpClass runtimeClass, ObjectClass objectClass, BumpClass functionClass) {
        super(runtimeClass, "Char", new HashMap<>(), createMethods(functionClass), objectClass, true);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("code", java.util.List.of(charMethod(functionClass, "code", 0, self -> (interpreter, arguments) ->
                interpreter.wrapIntegerValue(self.value()))));
        methods.put("upper", java.util.List.of(charMethod(functionClass, "upper", 0, self -> (interpreter, arguments) ->
                interpreter.wrapChar(Character.toUpperCase(self.value())))));
        methods.put("lower", java.util.List.of(charMethod(functionClass, "lower", 0, self -> (interpreter, arguments) ->
                interpreter.wrapChar(Character.toLowerCase(self.value())))));
        methods.put("is_digit", java.util.List.of(charMethod(functionClass, "is_digit", 0, self -> (interpreter, arguments) ->
                interpreter.wrapBooleanValue(Character.isDigit(self.value())))));
        methods.put("is_letter", java.util.List.of(charMethod(functionClass, "is_letter", 0, self -> (interpreter, arguments) ->
                interpreter.wrapBooleanValue(Character.isLetter(self.value())))));
        methods.put("is_whitespace", java.util.List.of(charMethod(functionClass, "is_whitespace", 0, self -> (interpreter, arguments) ->
                interpreter.wrapBooleanValue(Character.isWhitespace(self.value())))));
        methods.put("_add", java.util.List.of(charMethod(functionClass, "_add", 1, self -> (interpreter, arguments) -> {
            Object right = arguments.get(0);
            if (right instanceof CharInstance charInstance) {
                return interpreter.wrapString(Character.toString(self.value()) + charInstance.value());
            }
            if (right instanceof StringInstance stringInstance) {
                return interpreter.wrapString(Character.toString(self.value()) + stringInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot concatenate Char with " + BumpException.describeType(right) + ".");
        })));
        methods.put("_eq", java.util.List.of(charMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof CharInstance charInstance) {
                return interpreter.wrapBooleanValue(self.value() == charInstance.value());
            }
            return interpreter.wrapBooleanValue(false);
        })));
        return methods;
    }

    private static NativeMethod charMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<CharInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof CharInstance charInstance)) {
                throw BumpException.internal("Char method called on non-Char instance.");
            }
            return binder.apply(charInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof CharInstance charInstance) {
            return new CharInstance(this, charInstance.value());
        }
        if (value instanceof StringInstance stringInstance) {
            if (stringInstance.length() != 1) {
                throw BumpRuntimeError.valueError("Char() expects a single character String.");
            }
            return new CharInstance(this, stringInstance.charAt(0));
        }
        if (value instanceof String string) {
            if (string.length() != 1) {
                throw BumpRuntimeError.valueError("Char() expects a single character String.");
            }
            return new CharInstance(this, string.charAt(0));
        }
        throw BumpRuntimeError.typeError("Char() expects a Char or single character String, but got " + BumpException.describeType(value) + ".");
    }
}
