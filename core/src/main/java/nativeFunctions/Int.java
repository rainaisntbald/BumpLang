package nativeFunctions;

import bump.*;
import nativeClasses.FloatInstance;

import java.util.List;

public class Int extends NativeFunction {
    public Int(BumpClass runtimeClass) {
        super(runtimeClass, "int", 1);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof FloatInstance floatInstance) {
            if (floatInstance.value() % 1.0d != 0.0d) {
                throw BumpRuntimeError.valueError("Cannot convert " + BumpException.describeValue(value) + " to Integer.");
            }
            return interpreter.wrapInteger((int) floatInstance.value());
        }
        try {
            return interpreter.wrapInteger(Integer.valueOf(value.toString()));
        } catch (NumberFormatException e) {
            throw BumpRuntimeError.valueError("Cannot convert " + BumpException.describeValue(value) + " to Integer.");
        }
    }
}
