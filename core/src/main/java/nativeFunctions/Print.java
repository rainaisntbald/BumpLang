package nativeFunctions;

import java.util.List;
import bump.BumpClass;
import bump.Interpreter;
import bump.NativeFunction;

public class Print extends NativeFunction {
    public Print(BumpClass runtimeClass) {
        super(runtimeClass, "print", 1);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        System.out.println(value);
        return interpreter.wrapNull();
    }
}
