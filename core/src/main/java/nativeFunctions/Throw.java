package nativeFunctions;

import bump.BumpClass;
import bump.Interpreter;
import bump.NativeFunction;

import java.util.List;

public class Throw extends NativeFunction {
    public Throw(BumpClass runtimeClass) {
        super(runtimeClass, "throw", 1);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        interpreter.throwException(arguments.get(0));
        return interpreter.wrapNull();
    }
}
