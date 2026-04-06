package nativeClasses;

import bump.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloatClass extends BumpClass {
    public FloatClass(BumpClass runtimeClass, BumpClass superclass, List<BumpClass> interfaces, BumpClass functionClass) {
        super(runtimeClass, "Float", new HashMap<>(), new HashMap<>(), createMethods(functionClass), superclass, interfaces, null, true, false);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("_add", List.of(floatMethod(functionClass, "_add", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return new FloatInstance(self.getRuntimeClass(), self.value() + floatInstance.value());
            }
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return new FloatInstance(self.getRuntimeClass(), self.value() + integerInstance.value());
            }
            throw BumpRuntimeError.typeError("Cannot add " + BumpException.describeType(arguments.get(0)) + " to Float.");
        })));
        methods.put("_sub", List.of(floatMethod(functionClass, "_sub", 1, self -> (interpreter, arguments) -> numericBinary(self, arguments.get(0), "-"))));
        methods.put("_mul", List.of(floatMethod(functionClass, "_mul", 1, self -> (interpreter, arguments) -> numericBinary(self, arguments.get(0), "*"))));
        methods.put("_div", List.of(floatMethod(functionClass, "_div", 1, self -> (interpreter, arguments) -> numericBinary(self, arguments.get(0), "/"))));
        methods.put("_mod", List.of(floatMethod(functionClass, "_mod", 1, self -> (interpreter, arguments) -> numericBinary(self, arguments.get(0), "%"))));
        methods.put("_neg", List.of(floatMethod(functionClass, "_neg", 0, self -> (interpreter, arguments) ->
                new FloatInstance(self.getRuntimeClass(), -self.value()))));
        methods.put("_eq", List.of(floatMethod(functionClass, "_eq", 1, self -> (interpreter, arguments) -> {
            if (arguments.get(0) instanceof FloatInstance floatInstance) {
                return interpreter.wrapBooleanValue(self.value() == floatInstance.value());
            }
            if (arguments.get(0) instanceof IntegerInstance integerInstance) {
                return interpreter.wrapBooleanValue(self.value() == integerInstance.value());
            }
            return interpreter.wrapBooleanValue(false);
        })));
        methods.put("_gt", List.of(floatMethod(functionClass, "_gt", 1, self -> (interpreter, arguments) -> compare(interpreter, self, arguments.get(0), ">"))));
        methods.put("_gte", List.of(floatMethod(functionClass, "_gte", 1, self -> (interpreter, arguments) -> compare(interpreter, self, arguments.get(0), ">="))));
        methods.put("_lt", List.of(floatMethod(functionClass, "_lt", 1, self -> (interpreter, arguments) -> compare(interpreter, self, arguments.get(0), "<"))));
        methods.put("_lte", List.of(floatMethod(functionClass, "_lte", 1, self -> (interpreter, arguments) -> compare(interpreter, self, arguments.get(0), "<="))));
        return methods;
    }

    private static Object numericBinary(FloatInstance self, Object right, String operator) {
        double rightValue;
        if (right instanceof FloatInstance floatInstance) {
            rightValue = floatInstance.value();
        } else if (right instanceof IntegerInstance integerInstance) {
            rightValue = integerInstance.value();
        } else {
            throw BumpRuntimeError.typeError("Cannot apply " + operator + " to Float and " + BumpException.describeType(right) + ".");
        }
        if (("/".equals(operator) || "%".equals(operator)) && rightValue == 0.0d) {
            throw BumpRuntimeError.divisionByZero("Cannot divide by zero.");
        }
        return switch (operator) {
            case "-" -> new FloatInstance(self.getRuntimeClass(), self.value() - rightValue);
            case "*" -> new FloatInstance(self.getRuntimeClass(), self.value() * rightValue);
            case "/" -> new FloatInstance(self.getRuntimeClass(), self.value() / rightValue);
            case "%" -> new FloatInstance(self.getRuntimeClass(), self.value() % rightValue);
            default -> throw BumpException.internal("Unsupported Float operator " + operator + ".");
        };
    }

    private static Object compare(Interpreter interpreter, FloatInstance self, Object right, String operator) {
        double rightValue;
        if (right instanceof FloatInstance floatInstance) {
            rightValue = floatInstance.value();
        } else if (right instanceof IntegerInstance integerInstance) {
            rightValue = integerInstance.value();
        } else {
            throw BumpRuntimeError.typeError("Cannot compare Float to " + BumpException.describeType(right) + ".");
        }
        boolean result = switch (operator) {
            case ">" -> self.value() > rightValue;
            case ">=" -> self.value() >= rightValue;
            case "<" -> self.value() < rightValue;
            case "<=" -> self.value() <= rightValue;
            default -> throw BumpException.internal("Unsupported Float comparison " + operator + ".");
        };
        return interpreter.wrapBooleanValue(result);
    }

    private static NativeMethod floatMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<FloatInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof FloatInstance floatInstance)) {
                throw BumpException.internal("Float method called on non-Float instance.");
            }
            return binder.apply(floatInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof FloatInstance floatInstance) {
            return new FloatInstance(this, floatInstance.value());
        }
        if (value instanceof IntegerInstance integerInstance) {
            return new FloatInstance(this, integerInstance.value());
        }
        if (value instanceof StringInstance stringInstance) {
            return parse(stringInstance.value());
        }
        if (value instanceof String string) {
            return parse(string);
        }
        if (value instanceof Integer integer) {
            return new FloatInstance(this, integer.doubleValue());
        }
        if (value instanceof Double decimal) {
            return new FloatInstance(this, decimal);
        }
        throw BumpRuntimeError.typeError("Float() expects a Float, Integer, or numeric String value, but got " + BumpException.describeType(value) + ".");
    }

    private FloatInstance parse(String value) {
        try {
            return new FloatInstance(this, Double.parseDouble(value));
        } catch (NumberFormatException error) {
            throw BumpRuntimeError.valueError("Cannot convert \"" + value + "\" to Float.");
        }
    }
}
