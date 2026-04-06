package bump;

import ast.*;
import nativeClasses.ArrayClass;
import nativeClasses.ArrayInstance;
import nativeClasses.BoolClass;
import nativeClasses.BoolInstance;
import nativeClasses.CharClass;
import nativeClasses.CharInstance;
import nativeClasses.FloatClass;
import nativeClasses.FloatInstance;
import nativeClasses.FunctionClass;
import nativeClasses.IntegerClass;
import nativeClasses.IntegerInstance;
import nativeClasses.MapInstance;
import nativeClasses.NullClass;
import nativeClasses.NullInstance;
import nativeClasses.StringClass;
import nativeClasses.StringInstance;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

final class InterpreterRuntimeSupport {
    private record IdentityPair(Object left, Object right) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof IdentityPair pair)) {
                return false;
            }
            return left == pair.left && right == pair.right;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(left) + System.identityHashCode(right);
        }
    }

    private record CallFrame(String functionName, int line, int column, String context) {
        String render() {
            String location = BumpException.formatLocation(line > 0 ? line : null, column > 0 ? column : null);
            if (location != null) {
                return "at " + functionName + " (" + location + ")";
            }
            return "at " + functionName;
        }
    }

    private final Interpreter interpreter;
    private final Set<IdentityPair> equalityInProgress = new HashSet<>();
    private final Deque<CallFrame> callStack = new ArrayDeque<>();

    InterpreterRuntimeSupport(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    Object evalArrayLiteral(ArrayLiteral expr) {
        List<Object> values = new ArrayList<>();
        for (Expr element : expr.elements) {
            values.add(interpreter.eval(element));
        }
        return wrapArray(values);
    }

    Object evalMapLiteral(MapLiteral expr) {
        MapInstance map = new MapInstance((BumpClass) interpreter.globals.get("Map"));
        for (int i = 0; i < expr.keys.size(); i++) {
            Object key = interpreter.eval(expr.keys.get(i));
            Object value = interpreter.eval(expr.values.get(i));
            int existingIndex = -1;
            for (int j = 0; j < map.keys().size(); j++) {
                if (valuesEqual(map.keys().get(j), key)) {
                    existingIndex = j;
                    break;
                }
            }
            if (existingIndex == -1) {
                map.keys().add(key);
                map.values().add(value);
            } else {
                map.values().set(existingIndex, value);
            }
        }
        return map;
    }

    Object evalArrayIndex(ArrayIndex expr) {
        int index = requireIndexValue(interpreter.eval(expr.index));
        Object array = interpreter.eval(expr.array);
        if (array instanceof ArrayInstance arrayInstance) {
            return getIndexedValue(arrayInstance.values(), index, "array");
        }
        if (array instanceof List<?> list) {
            return getIndexedValue(list, index, "array");
        }
        if (array instanceof StringInstance stringInstance) {
            return getIndexedCharacter(stringInstance.value(), index);
        }
        if (array instanceof String string) {
            return getIndexedCharacter(string, index);
        }
        throw BumpRuntimeError.typeError("Cannot index value of type " + BumpException.describeType(array) + ".");
    }

    Object evalSetIndex(SetIndex expr) {
        Object target = interpreter.eval(expr.array);
        int intIndex = requireIndexValue(interpreter.eval(expr.index));
        Object value = interpreter.eval(expr.value);

        if (target instanceof ArrayInstance arrayInstance) {
            setIndexedValue(arrayInstance.values(), intIndex, value, "array");
            return value;
        }
        if (target instanceof List<?> list) {
            setIndexedValue(list, intIndex, value, "array");
            return value;
        }
        throw BumpRuntimeError.typeError("Cannot assign through an index on value of type " + BumpException.describeType(target) + ".");
    }

    Object evalCompoundSetExpr(CompoundSetExpr expr) {
        if (expr.target instanceof VariableExpr variableExpr) {
            Object current = interpreter.eval(variableExpr);
            Object value = interpreter.eval(expr.value);
            Object result = applyCompoundOperator(current, expr.operator, value);
            Integer distance = interpreter.locals.get(expr);
            if (distance != null) {
                interpreter.environment.assignAt(distance, variableExpr.name, result);
            } else {
                interpreter.environment.assign(variableExpr.name, result);
            }
            return expr.returnsPreviousValue ? current : result;
        }

        if (expr.target instanceof GetExpr getExpr) {
            Object object = interpreter.eval(getExpr.object);
            if (!(object instanceof BumpInstance instance)) {
                throw BumpRuntimeError.propertyError("Cannot assign property '" + getExpr.name + "' on value of type " + BumpException.describeType(object) + ".");
            }
            Object current = instance.get(getExpr.name);
            Object value = interpreter.eval(expr.value);
            Object result = applyCompoundOperator(current, expr.operator, value);
            instance.set(getExpr.name, result);
            return expr.returnsPreviousValue ? current : result;
        }

        if (expr.target instanceof ArrayIndex arrayIndex) {
            Object target = interpreter.eval(arrayIndex.array);
            int intIndex = requireIndexValue(interpreter.eval(arrayIndex.index));
            if (target instanceof ArrayInstance arrayInstance) {
                return applyIndexedCompoundSet(arrayInstance.values(), intIndex, expr, expr.returnsPreviousValue);
            }
            if (target instanceof List<?> list) {
                return applyIndexedCompoundSet(list, intIndex, expr, expr.returnsPreviousValue);
            }
            throw BumpRuntimeError.typeError("Cannot assign through an index on value of type " + BumpException.describeType(target) + ".");
        }

        throw BumpRuntimeError.typeError("Invalid assignment target.");
    }

    Object evalCallExpr(CallExpr expr) {
        Object callee = interpreter.eval(expr.callee);
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(interpreter.eval(argument));
        }

        if (!(callee instanceof BumpCallable function)) {
            throw BumpRuntimeError.typeError("Cannot call value of type " + BumpException.describeType(callee) + ".");
        }
        if (!function.acceptsArity(arguments.size())) {
            throw BumpRuntimeError.arityError("Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }

        Map<String, BumpClass> typeArgs = extractTypeArguments(expr, callee);

        if (callee instanceof BumpClass bumpClass && !typeArgs.isEmpty()) {
            interpreter.pendingTypeArguments = typeArgs;
        }

        callStack.push(new CallFrame(describeCallable(expr.callee, function), expr.getLine(), expr.getColumn(), expr.describeLocation()));
        try {
            return function.call(interpreter, arguments);
        } finally {
            interpreter.pendingTypeArguments = null;
            callStack.pop();
        }
    }

    private Map<String, BumpClass> extractTypeArguments(CallExpr expr, Object callee) {
        Map<String, BumpClass> result = new HashMap<>();
        if (!(callee instanceof BumpClass bumpClass)) {
            return result;
        }
        List<String> typeArgNames = new ArrayList<>(expr.typeArguments);
        if (typeArgNames.isEmpty() && expr.callee instanceof VariableExpr varExpr) {
            String fullName = varExpr.name;
            int ltIndex = fullName.indexOf('<');
            if (ltIndex != -1) {
                int gtIndex = fullName.lastIndexOf('>');
                if (gtIndex > ltIndex) {
                    String typeArgsString = fullName.substring(ltIndex + 1, gtIndex);
                    typeArgNames = parseTypeArguments(typeArgsString);
                }
            }
        }
        if (typeArgNames.isEmpty()) {
            return result;
        }

        List<String> typeParamNames = getTypeParameterNames(bumpClass);

        for (int i = 0; i < Math.min(typeArgNames.size(), typeParamNames.size()); i++) {
            String typeArgName = typeArgNames.get(i).trim();
            String typeParamName = typeParamNames.get(i);

            BumpClass typeClass = lookupClass(typeArgName);
            if (typeClass != null) {
                result.put(typeParamName, typeClass);
            }
        }

        return result;
    }

    private List<String> parseTypeArguments(String typeArgsString) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < typeArgsString.length(); i++) {
            char c = typeArgsString.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(typeArgsString.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(typeArgsString.substring(start).trim());
        return result;
    }

    private List<String> getTypeParameterNames(BumpClass bumpClass) {
        return bumpClass.getTypeParameterNames();
    }

    private BumpClass lookupClass(String name) {
        String lookupName = Interpreter.rawTypeName(name);
        try {
            Object value = interpreter.environment.get(lookupName);
            if (value instanceof BumpClass bc) {
                return bc;
            }
        } catch (BumpRuntimeError ignored) {
        }

        try {
            Object value = interpreter.globals.get(lookupName);
            return value instanceof BumpClass bc ? bc : null;
        } catch (BumpRuntimeError e) {
            return null;
        }
    }

    Object evalGetExpr(GetExpr expr) {
        Object object = interpreter.eval(expr.object);
        if (object instanceof BumpInstance instance) {
            return instance.get(expr.name);
        }
        throw BumpRuntimeError.propertyError("Cannot access property '" + expr.name + "' on value of type " + BumpException.describeType(object) + ".");
    }

    Object evalSetExpr(SetExpr expr) {
        Object object = expr.object == null ? null : interpreter.eval(expr.object);

        if (object == null) {
            Object value = interpreter.eval(expr.value);
            Integer distance = interpreter.locals.get(expr);
            if (distance != null) {
                interpreter.environment.assignAt(distance, expr.name, value);
            } else {
                interpreter.environment.assign(expr.name, value);
            }
            return value;
        }

        if (object instanceof BumpInstance instance) {
            Object value = interpreter.eval(expr.value);
            instance.set(expr.name, value);
            return value;
        }

        throw BumpRuntimeError.propertyError("Cannot assign property '" + expr.name + "' on value of type " + BumpException.describeType(object) + ".");
    }

    Object evalSuperExpr(SuperExpr expr) {
        Integer distance = interpreter.locals.get(expr);
        if (distance == null) {
            throw BumpException.internal("Resolver did not record a scope depth for super.");
        }

        Object superValue = interpreter.environment.getAt(distance, "super");
        Object thisValue = interpreter.environment.getAt(distance - 1, "this");

        if (!(superValue instanceof BumpClass superclass)) {
            throw BumpRuntimeError.propertyError("Cannot access super on a value of type " + BumpException.describeType(superValue) + ".");
        }
        if (!(thisValue instanceof BumpInstance instance)) {
            throw BumpRuntimeError.propertyError("Cannot access super on a value of type " + BumpException.describeType(thisValue) + ".");
        }

        List<BumpMethod> methods = superclass.findMethods(expr.member.getText());
        if (methods.isEmpty()) {
            throw BumpRuntimeError.propertyError("Undefined superclass method '" + expr.member.getText() + "'.");
        }
        if (methods.size() == 1) {
            return methods.get(0).bind(instance);
        }
        List<BumpCallable> bound = methods.stream().map(method -> method.bind(instance)).toList();
        return new OverloadedFunction(interpreter.functionClass(), expr.member.getText(), bound);
    }

    boolean isTrue(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof BoolInstance boolInstance) return boolInstance.value();
        throw BumpRuntimeError.typeError("Expected a Bool value, but got " + BumpException.describeType(val) + ".");
    }

    Object coerceBoolean(Object value) {
        if (value instanceof BoolInstance) {
            return value;
        }
        if (value instanceof Boolean bool) {
            return wrapBoolean(bool);
        }
        return value;
    }

    boolean valuesEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (isNullish(left) || isNullish(right)) {
            return isNullish(left) && isNullish(right);
        }

        IdentityPair pair = new IdentityPair(left, right);
        if (equalityInProgress.contains(pair)) {
            return true;
        }

        equalityInProgress.add(pair);
        try {
            return requireBooleanResult(callBinaryMethod(left, "_eq", right), "Equality");
        } finally {
            equalityInProgress.remove(pair);
        }
    }

    Object callUnaryMethod(Object receiver, String methodName) {
        return callMethod(receiver, methodName, new ArrayList<>());
    }

    Object callBinaryMethod(Object receiver, String methodName, Object argument) {
        return callMethod(receiver, methodName, List.of(argument));
    }

    Object applyCompoundOperator(Object receiver, TokenType operator, Object argument) {
        return switch (operator) {
            case PLUSASSIGN -> callBinaryMethod(receiver, "_add", argument);
            case MINUSASSIGN -> callBinaryMethod(receiver, "_sub", argument);
            case MULASSIGN -> callBinaryMethod(receiver, "_mul", argument);
            case DIVASSIGN -> callBinaryMethod(receiver, "_div", argument);
            default -> throw BumpException.internal("Unsupported compound assignment operator: " + operator);
        };
    }

    boolean requireBooleanResult(Object value, String operation) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof BoolInstance boolInstance) {
            return boolInstance.value();
        }
        throw BumpRuntimeError.typeError(operation + " must return a Bool, but got " + BumpException.describeType(value) + ".");
    }

    BoolInstance wrapBooleanValue(boolean value) {
        return wrapBoolean(value);
    }

    StringInstance wrapString(String value) {
        return new StringInstance(stringClass(), value);
    }

    CharInstance wrapChar(char value) {
        return new CharInstance(charClass(), value);
    }

    IntegerInstance wrapInteger(int value) {
        return new IntegerInstance(integerClass(), value);
    }

    FloatInstance wrapFloat(double value) {
        return new FloatInstance(floatClass(), value);
    }

    IntegerInstance wrapIntegerValue(int value) {
        return wrapInteger(value);
    }

    ArrayInstance wrapArray(List<Object> values) {
        return new ArrayInstance(arrayClass(), values);
    }

    NullInstance wrapNull() {
        return new NullInstance(nullClass());
    }

    void throwException(Object value) {
        if (!(value instanceof BumpInstance instance) || !instance.getRuntimeClass().isSubclassOf(interpreter.exceptionClass)) {
            throw BumpRuntimeError.typeError("Can only throw Exception values, but got " + BumpException.describeType(value) + ".");
        }
        attachThrowMetadataIfMissing(instance);
        attachStackTraceIfMissing(instance);
        throw new Interpreter.ThrownException(instance);
    }

    BumpException uncaughtThrownException(Interpreter.ThrownException thrown) {
        return BumpException.runtime(describeException(thrown.exception));
    }

    BumpInstance runtimeErrorAsException(BumpRuntimeError error) {
        BumpClass runtimeClass = interpreter.globals.getType(error.type().builtinClassName());
        BumpInstance exception = new BumpInstance(runtimeClass);
        runtimeClass.initializeFields(exception, interpreter);
        exception.set("message", wrapString(error.diagnostic().getDetail()));
        setOptionalIntegerField(exception, Builtins.EXCEPTION_LINE, error.diagnostic().getLine());
        setOptionalIntegerField(exception, Builtins.EXCEPTION_COLUMN, error.diagnostic().getColumn());
        setOptionalStringField(exception, Builtins.EXCEPTION_CONTEXT, error.diagnostic().getContext());
        exception.set(Builtins.EXCEPTION_STACK_TRACE, wrapArray(captureStackTrace()));
        return exception;
    }

    int requireIndexValue(Object value) {
        if (value instanceof IntegerInstance integerInstance) {
            return integerInstance.value();
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        throw BumpRuntimeError.typeError("Array and string indices must be Integer values, but got " + BumpException.describeType(value) + ".");
    }

    boolean matchesCatch(TryStmt stmt, BumpInstance exception) {
        if (stmt.catchParameter == null) {
            return true;
        }
        BumpClass catchType = interpreter.environment.getType(
                Interpreter.rawTypeName(stmt.catchParameter.getResolvedType() != null
                        ? stmt.catchParameter.getResolvedType().name()
                        : stmt.catchParameter.typeName)
        );
        return exception.getRuntimeClass().isSubclassOf(catchType);
    }

    void executeCatch(TryStmt stmt, BumpInstance exception) {
        if (stmt.catchParameter == null) {
            interpreter.execute(stmt.catchBlock);
            return;
        }
        Environment catchEnvironment = new Environment(interpreter.environment);
        BumpClass catchType = interpreter.environment.getType(
                Interpreter.rawTypeName(stmt.catchParameter.getResolvedType() != null
                        ? stmt.catchParameter.getResolvedType().name()
                        : stmt.catchParameter.typeName)
        );
        catchEnvironment.define(stmt.catchParameter.name, exception, catchType);
        interpreter.executeBlock(stmt.catchBlock.statements, catchEnvironment);
    }

    private BoolInstance wrapBoolean(boolean value) {
        return new BoolInstance(boolClass(), value);
    }

    private Object applyIndexedCompoundSet(List<?> values, int index, CompoundSetExpr expr, boolean returnsPreviousValue) {
        Object current = getIndexedValue(values, index, "array");
        Object value = interpreter.eval(expr.value);
        Object result = applyCompoundOperator(current, expr.operator, value);
        setIndexedValue(values, index, result, "array");
        return returnsPreviousValue ? current : result;
    }

    private Object callMethod(Object receiver, String methodName, List<Object> arguments) {
        if (!(receiver instanceof BumpInstance instance)) {
            throw BumpRuntimeError.typeError("Cannot call method '" + methodName + "' on value of type " + BumpException.describeType(receiver) + ".");
        }

        Object method = instance.get(methodName);
        if (!(method instanceof BumpCallable callable)) {
            throw BumpRuntimeError.typeError("Property '" + methodName + "' on " + instance.runtimeClass.name + " is not callable.");
        }

        if (!callable.acceptsArity(arguments.size())) {
            throw BumpRuntimeError.arityError("Expected " + callable.arity() + " arguments but got " + arguments.size() + ".");
        }

        return callable.call(interpreter, arguments);
    }

    private NullClass nullClass() {
        return (NullClass) interpreter.globals.get("Null");
    }

    private FunctionClass functionClass() {
        return interpreter.functionClass;
    }

    private StringClass stringClass() {
        return (StringClass) interpreter.globals.get("String");
    }

    private IntegerClass integerClass() {
        return (IntegerClass) interpreter.globals.get("Integer");
    }

    private FloatClass floatClass() {
        return (FloatClass) interpreter.globals.get("Float");
    }

    private ArrayClass arrayClass() {
        return (ArrayClass) interpreter.globals.get("Array");
    }

    private BoolClass boolClass() {
        return (BoolClass) interpreter.globals.get("Bool");
    }

    private CharClass charClass() {
        return (CharClass) interpreter.globals.get("Char");
    }

    private boolean isNullish(Object value) {
        return value == null || value instanceof NullInstance;
    }

    private String describeException(BumpInstance exception) {
        StringBuilder builder = new StringBuilder();
        Object message = exception.fields.get(Builtins.EXCEPTION_MESSAGE);
        if (message instanceof StringInstance stringInstance && !stringInstance.value().isEmpty()) {
            builder.append("Uncaught ").append(exception.getRuntimeClass().name).append(": ").append(stringInstance.value()).append(".");
        } else {
            builder.append("Uncaught ").append(exception.getRuntimeClass().name).append(".");
        }
        List<String> stackFrames = readStackTrace(exception);
        for (String frame : stackFrames) {
            builder.append("\n  ").append(frame);
        }
        return builder.toString();
    }

    private void attachStackTraceIfMissing(BumpInstance exception) {
        Object existing = exception.fields.get(Builtins.EXCEPTION_STACK_TRACE);
        if (existing instanceof ArrayInstance arrayInstance && !arrayInstance.values().isEmpty()) {
            return;
        }
        if (existing instanceof List<?> list && !list.isEmpty()) {
            return;
        }
        exception.set(Builtins.EXCEPTION_STACK_TRACE, wrapArray(captureStackTrace()));
    }

    private void attachThrowMetadataIfMissing(BumpInstance exception) {
        CallFrame frame = callStack.peek();
        if (frame == null) {
            return;
        }
        if (isUnsetField(exception.fields.get(Builtins.EXCEPTION_LINE))) {
            exception.set(Builtins.EXCEPTION_LINE, wrapInteger(frame.line()));
        }
        if (isUnsetField(exception.fields.get(Builtins.EXCEPTION_COLUMN))) {
            exception.set(Builtins.EXCEPTION_COLUMN, wrapInteger(frame.column()));
        }
        if (isUnsetField(exception.fields.get(Builtins.EXCEPTION_CONTEXT)) && frame.context() != null) {
            exception.set(Builtins.EXCEPTION_CONTEXT, wrapString(frame.context()));
        }
    }

    private List<Object> captureStackTrace() {
        List<Object> frames = new ArrayList<>();
        for (CallFrame frame : callStack) {
            frames.add(wrapString(frame.render()));
        }
        return frames;
    }

    private List<String> readStackTrace(BumpInstance exception) {
        Object stack = exception.fields.get(Builtins.EXCEPTION_STACK_TRACE);
        List<String> frames = new ArrayList<>();
        if (stack instanceof ArrayInstance arrayInstance) {
            for (Object value : arrayInstance.values()) {
                frames.add(BumpException.describeValue(value));
            }
        } else if (stack instanceof List<?> list) {
            for (Object value : list) {
                frames.add(BumpException.describeValue(value));
            }
        }
        return frames;
    }

    private void setOptionalIntegerField(BumpInstance exception, String fieldName, Integer value) {
        exception.set(fieldName, value != null ? wrapInteger(value) : wrapNull());
    }

    private void setOptionalStringField(BumpInstance exception, String fieldName, String value) {
        exception.set(fieldName, value != null ? wrapString(value) : wrapNull());
    }

    private String describeCallable(Expr callee, BumpCallable callable) {
        if (callable instanceof AbstractFunction function && function.name != null) {
            return function.name;
        }
        if (callable instanceof BumpClass bumpClass) {
            return bumpClass.name;
        }
        if (callee instanceof GetExpr getExpr) {
            return getExpr.name;
        }
        if (callee instanceof VariableExpr variableExpr) {
            return variableExpr.name;
        }
        return "<anonymous>";
    }

    private boolean isUnsetField(Object value) {
        return value == null || value instanceof NullInstance;
    }

    private Object getIndexedValue(List<?> values, int index, String collectionName) {
        if (index < 0 || index >= values.size()) {
            throw BumpRuntimeError.indexError(capitalize(collectionName) + " index " + index + " is out of bounds for length " + values.size() + ".");
        }
        return values.get(index);
    }

    private void setIndexedValue(List<?> values, int index, Object value, String collectionName) {
        if (index < 0 || index >= values.size()) {
            throw BumpRuntimeError.indexError(capitalize(collectionName) + " index " + index + " is out of bounds for length " + values.size() + ".");
        }
        mutableList(values).set(index, value);
    }

    private CharInstance getIndexedCharacter(String value, int index) {
        if (index < 0 || index >= value.length()) {
            throw BumpRuntimeError.indexError("String index " + index + " is out of bounds for length " + value.length() + ".");
        }
        return wrapChar(value.charAt(index));
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @SuppressWarnings("unchecked")
    private List<Object> mutableList(List<?> values) {
        return (List<Object>) values;
    }
}
