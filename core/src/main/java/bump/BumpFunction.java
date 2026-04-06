package bump;

import ast.FunctionLiteralExpr;
import ast.Parameter;
import nativeClasses.NullInstance;

import java.util.List;
import java.util.Map;

public class BumpFunction extends AbstractFunction implements BumpMethod {
    public final FunctionLiteralExpr declaration;
    public final Environment closure;
    public final boolean isInitializer;

    public BumpFunction(BumpClass runtimeClass, String name, FunctionLiteralExpr declaration, Environment closure, boolean isInitializer) {
        super(runtimeClass, name, declaration.parameters.size());
        this.declaration = declaration;
        this.closure = closure;
        this.isInitializer = isInitializer;
    }

    @Override
    public BumpCallable bind(BumpInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        for (Map.Entry<String, BumpClass> typeArg : instance.typeArguments.entrySet()) {
            environment.define(typeArg.getKey(), typeArg.getValue());
        }
        return new BumpFunction(getRuntimeClass(), name, declaration, environment, isInitializer);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        if (declaration.isAbstractMethod) {
            throw BumpException.internal("Cannot invoke abstract method '" + (name != null ? name : "<anonymous>") + "'.");
        }
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.parameters.size(); i++) {
            Parameter parameter = declaration.parameters.get(i);
            BumpClass declaredType = declaredRuntimeType(parameter.getResolvedType(), parameter.typeName);
            environment.define(parameter.name, arguments.get(i), declaredType);
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Interpreter.ReturnException returnValue) {
            if (isInitializer) return closure.get("this");
            return validateReturnValue(returnValue.value);
        }

        if (isInitializer) return closure.get("this");
        return validateReturnValue(new NullInstance(((Interpreter) interpreter).nullClass()));
    }

    private Object validateReturnValue(Object value) {
        if (declaration.getResolvedReturnType() == null) {
            return value;
        }
        BumpClass declaredType = declaredRuntimeType(declaration.getResolvedReturnType(), declaration.returnTypeName);
        if (declaredType == null) {
            return value;
        }
        if (value == null || value instanceof NullInstance) {
            return value;
        }
        if (value instanceof BumpInstance instance && instance.getRuntimeClass().isSubclassOf(declaredType)) {
            return value;
        }
        String functionName = name != null ? name : "<anonymous>";
        throw BumpRuntimeError.typeError("Function '" + functionName + "' must return " + declaredType.name + ", but got " + BumpException.describeType(value) + ".");
    }

    private BumpClass declaredRuntimeType(SemanticType semanticType, String fallbackTypeName) {
        if (semanticType != null) {
            if (semanticType.isTypeParameter()) {
                SemanticType upperBound = semanticType.upperBound();
                if (upperBound == null) {
                    return null;
                }
                return closure.getType(Interpreter.rawTypeName(upperBound.name()));
            }
            return closure.getType(Interpreter.rawTypeName(semanticType.name()));
        }
        if (fallbackTypeName == null) {
            return null;
        }
        return closure.getType(Interpreter.rawTypeName(fallbackTypeName));
    }
}
