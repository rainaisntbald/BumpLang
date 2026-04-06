package nativeFunctions;

import bump.BumpClass;
import bump.Interpreter;
import bump.NativeFunction;

import java.util.List;

public class Str extends NativeFunction {

    public Str(BumpClass runtimeClass) {
        super(runtimeClass, "str", 1);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        return interpreter.wrapString(value.toString());
    }
}
