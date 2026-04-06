package nativeClasses;

import bump.BumpException;
import bump.BumpCallable;
import bump.BumpClass;
import bump.BumpInstance;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;
import bump.NativeFunction;

import java.util.List;
import java.util.function.Function;

public class NativeMethod implements BumpMethod {
    @FunctionalInterface
    public interface BoundCall {
        Object call(Interpreter interpreter, List<Object> arguments);
    }

    private static final class BoundNativeFunction extends NativeFunction {
        private final BoundCall boundCall;

        private BoundNativeFunction(BumpClass runtimeClass, String name, int arity, BoundCall boundCall) {
            super(runtimeClass, name, arity);
            this.boundCall = boundCall;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return boundCall.call(interpreter, arguments);
        }
    }

    private final BumpClass functionClass;
    private final String name;
    private final int arity;
    private final Function<BumpInstance, BoundCall> binder;

    public NativeMethod(BumpClass functionClass, String name, int arity, Function<BumpInstance, BoundCall> binder) {
        this.functionClass = functionClass;
        this.name = name;
        this.arity = arity;
        this.binder = binder;
    }

    @Override
    public int arity() {
        return arity;
    }

    @Override
    public BumpCallable bind(BumpInstance instance) {
        BoundCall boundCall = binder.apply(instance);
        return new BoundNativeFunction(functionClass, name, arity, boundCall);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        throw BumpException.internal("Native method must be bound before calling.");
    }
}
