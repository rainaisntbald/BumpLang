package nativeFunctions;

import bump.*;

import java.util.List;

public class Implements extends NativeFunction {
    public Implements(BumpClass runtimeClass) {
        super(runtimeClass, "implements", 2);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        Object interfaceClass = arguments.get(1);

        if (!(interfaceClass instanceof BumpClass bumpClass)) {
            throw BumpRuntimeError.typeError("Second argument to 'implements' must be a class, got " + BumpException.describeValue(interfaceClass) + ".");
        }

        if (!(value instanceof BumpInstance bumpInstance)) {
            throw BumpRuntimeError.typeError("First argument to 'implements' must be an instance, got " + BumpException.describeValue(value) + ".");
        }

        BumpClass valueClass = bumpInstance.getRuntimeClass();
        boolean implementsInterface = valueClass.isSubclassOf(bumpClass);

        return interpreter.wrapBooleanValue(implementsInterface);
    }
}
