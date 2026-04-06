package nativeFunctions;

import bump.*;

import java.util.List;

public class TypeOf extends NativeFunction {
    public TypeOf(BumpClass runtimeClass) {
        super(runtimeClass, "type_of", 1);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if(value instanceof BumpInstance bumpInstance) {
            return bumpInstance.getRuntimeClass();
        }
        throw BumpRuntimeError.typeError("Cannot determine the type of value " + BumpException.describeValue(value) + ".");
    }

}
