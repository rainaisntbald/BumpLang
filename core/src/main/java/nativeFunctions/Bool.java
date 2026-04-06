package nativeFunctions;

import bump.*;
import nativeClasses.BoolInstance;
import nativeClasses.FloatInstance;
import nativeClasses.IntegerInstance;
import nativeClasses.NullInstance;
import nativeClasses.StringInstance;

import java.util.List;

public class Bool extends NativeFunction {

    public Bool(BumpClass runtimeClass) {
        super(runtimeClass, "bool", 1);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value == null || value instanceof NullInstance) {
            return interpreter.wrapBooleanValue(false);
        }
        if (value instanceof BoolInstance boolInstance) {
            return interpreter.wrapBooleanValue(boolInstance.value());
        }
        if (value instanceof Boolean bool) {
            return interpreter.wrapBooleanValue(bool);
        }
        if (value instanceof IntegerInstance integerInstance) {
            return interpreter.wrapBooleanValue(integerInstance.value() != 0);
        }
        if (value instanceof Integer integer) {
            return interpreter.wrapBooleanValue(integer != 0);
        }
        if (value instanceof FloatInstance floatInstance) {
            return interpreter.wrapBooleanValue(floatInstance.value() != 0.0d);
        }
        if (value instanceof Double decimal) {
            return interpreter.wrapBooleanValue(decimal != 0.0d);
        }
        if (value instanceof StringInstance stringInstance) {
            return parseStringBoolean(interpreter, stringInstance.value());
        }
        if (value instanceof String string) {
            return parseStringBoolean(interpreter, string);
        }
        throw BumpRuntimeError.valueError("Cannot convert " + BumpException.describeValue(value) + " to Bool.");
    }

    private Object parseStringBoolean(Interpreter interpreter, String value) {
        if (value.equalsIgnoreCase("true")) {
            return interpreter.wrapBooleanValue(true);
        }
        if (value.equalsIgnoreCase("false")) {
            return interpreter.wrapBooleanValue(false);
        }
        throw BumpRuntimeError.valueError("Cannot convert \"" + value + "\" to Bool. Expected \"true\" or \"false\".");
    }
}
