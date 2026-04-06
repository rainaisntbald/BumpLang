package bump;

import ast.Parameter;
import nativeClasses.NullInstance;

import java.util.ArrayList;
import java.util.List;

public class OverloadedFunction extends AbstractFunction {
    private final List<BumpCallable> overloads;

    public OverloadedFunction(BumpClass runtimeClass, String name, List<? extends BumpCallable> overloads) {
        super(runtimeClass, name, -1);
        this.overloads = List.copyOf(overloads);
    }

    public List<BumpCallable> overloads() {
        return overloads;
    }

    public OverloadedFunction append(BumpCallable overload) {
        List<BumpCallable> combined = new ArrayList<>(overloads);
        combined.add(overload);
        return new OverloadedFunction(getRuntimeClass(), name, combined);
    }

    @Override
    public boolean acceptsArity(int argumentCount) {
        return overloads.stream().anyMatch(overload -> overload.acceptsArity(argumentCount));
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        BumpCallable selected = selectCallable(name, overloads, arguments);
        return selected.call(interpreter, arguments);
    }

    public static BumpCallable selectCallable(String name, List<? extends BumpCallable> overloads, List<Object> arguments) {
        BumpCallable best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;

        for (BumpCallable overload : overloads) {
            int score = matchScore(overload, arguments);
            if (score < 0) {
                continue;
            }
            if (score < bestScore) {
                best = overload;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }

        if (best == null) {
            throw BumpRuntimeError.arityError("No overload of '" + displayName(name) + "' matches " + arguments.size() + " arguments.");
        }
        if (ambiguous) {
            throw BumpRuntimeError.typeError("Call to overloaded function '" + displayName(name) + "' is ambiguous for the given argument types.");
        }
        return best;
    }

    private static int matchScore(BumpCallable overload, List<Object> arguments) {
        if (!overload.acceptsArity(arguments.size())) {
            return -1;
        }

        if (overload instanceof BumpFunction function) {
            return matchBumpFunction(function, arguments);
        }

        if (overload.arity() == arguments.size()) {
            return 10_000;
        }
        return -1;
    }

    private static int matchBumpFunction(BumpFunction function, List<Object> arguments) {
        if (function.declaration.parameters.size() != arguments.size()) {
            return -1;
        }

        int score = 0;
        for (int i = 0; i < arguments.size(); i++) {
            Parameter parameter = function.declaration.parameters.get(i);
            BumpClass declaredType = null;
            if (parameter.getResolvedType() != null) {
                if (!parameter.getResolvedType().isTypeParameter()) {
                    declaredType = function.closure.getType(Interpreter.rawTypeName(parameter.getResolvedType().name()));
                }
            }
            int parameterScore = argumentScore(arguments.get(i), declaredType);
            if (parameterScore < 0) {
                return -1;
            }
            score += parameterScore;
        }
        return score;
    }

    private static int argumentScore(Object argument, BumpClass declaredType) {
        if (declaredType == null) {
            return 0;
        }

        if (argument == null || argument instanceof NullInstance) {
            return 100;
        }
        if (!(argument instanceof BumpInstance instance)) {
            return 100;
        }

        BumpClass runtimeClass = instance.getRuntimeClass();
        if (runtimeClass == declaredType) {
            return 0;
        }
        if (runtimeClass.isNamedOrSubclassOf(declaredType.name)) {
            return 1;
        }
        return -1;
    }

    private static String displayName(String name) {
        return name == null ? "<anonymous>" : name;
    }
}
