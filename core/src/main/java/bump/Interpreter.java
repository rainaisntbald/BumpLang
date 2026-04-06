package bump;

import ast.*;
import nativeClasses.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Interpreter implements Visitor<Object> {
    private record EnumCaseRuntimeMatch(boolean matched, String bindingName, Object bindingValue) {}

    final Environment globals =  new Environment();
    Environment environment = new Environment(globals);
    final Map<Expr, Integer> locals = new HashMap<>();
    final ClassClass classClass;
    final FunctionClass functionClass;
    final ObjectClass objectClass;
    final BumpClass exceptionClass;
    private final InterpreterRuntimeSupport runtime;
    private final Set<VariableDeclarationStmt> hoistedFunctionDeclarations =
            Collections.newSetFromMap(new IdentityHashMap<>());
    Map<String, BumpClass> pendingTypeArguments = null;

    public Interpreter() {
        Builtins.RuntimeState builtins = Builtins.installRuntime(globals);
        classClass = builtins.classClass();
        functionClass = builtins.functionClass();
        objectClass = builtins.objectClass();
        exceptionClass = builtins.exceptionClass();
        runtime = new InterpreterRuntimeSupport(this);
    }

    public static class ReturnException extends RuntimeException {
        final Object value;
        ReturnException(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    public static class ThrownException extends RuntimeException {
        final BumpInstance exception;

        ThrownException(BumpInstance exception) {
            super(null, null, false, false);
            this.exception = exception;
        }
    }

    public static class BreakException extends RuntimeException {
        BreakException() {
            super(null, null, false, false);
        }
    }

    public static class ContinueException extends RuntimeException {
        ContinueException() {
            super(null, null, false, false);
        }
    }


    public Object eval(Expr expr) {
        try {
            return expr.accept(this);
        } catch (ReturnException | ThrownException | BreakException | ContinueException controlFlow) {
            throw controlFlow;
        } catch (BumpRuntimeError error) {
            throw new ThrownException(runtime.runtimeErrorAsException(error.withFallbackLocation(expr.getLine(), expr.getColumn(), expr.getLength(), expr.describeLocation())));
        } catch (BumpException error) {
            throw error.withFallbackLocation(expr.getLine(), expr.getColumn(), expr.getLength(), expr.describeLocation());
        }
    }

    public void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    public void execute(Stmt stmt) {
        try {
            stmt.accept(this);
        } catch (ReturnException | ThrownException | BreakException | ContinueException controlFlow) {
            throw controlFlow;
        } catch (BumpRuntimeError error) {
            throw new ThrownException(runtime.runtimeErrorAsException(error.withFallbackLocation(stmt.getLine(), stmt.getColumn(), stmt.getLength(), stmt.describeLocation())));
        } catch (BumpException error) {
            throw error.withFallbackLocation(stmt.getLine(), stmt.getColumn(), stmt.getLength(), stmt.describeLocation());
        }
    }

    public void predeclareTopLevelFunctions(List<Stmt> program) {
        for (Stmt stmt : program) {
            if (!(stmt instanceof VariableDeclarationStmt declaration)
                    || !"Function".equals(declaration.typeName)
                    || !(declaration.value instanceof FunctionLiteralExpr functionLiteral)) {
                continue;
            }

            BumpCallable function = new BumpFunction(functionClass, declaration.name, functionLiteral, environment, false);
            if (environment.containsLocal(declaration.name)) {
                Object existing = environment.getLocal(declaration.name);
                if (existing instanceof OverloadedFunction overloaded) {
                    environment.replaceLocal(declaration.name, overloaded.append(function));
                    hoistedFunctionDeclarations.add(declaration);
                    continue;
                }
                if (existing instanceof BumpCallable callable) {
                    environment.replaceLocal(
                            declaration.name,
                            new OverloadedFunction(functionClass, declaration.name, List.of(callable, function))
                    );
                    hoistedFunctionDeclarations.add(declaration);
                    continue;
                }
            } else {
                BumpClass declaredType = runtimeTypeFromSemantic(declaration.getResolvedType());
                if (declaredType == null && declaration.typeName != null) {
                    declaredType = environment.getType(rawTypeName(declaration.typeName));
                }
                environment.define(declaration.name, function, declaredType);
                hoistedFunctionDeclarations.add(declaration);
            }
        }
    }

    @Override
    public Object visitIntegerLiteral(IntegerLiteral expr) {
        return runtime.wrapInteger(expr.value);
    }

    @Override
    public Object visitFloatLiteral(FloatLiteral expr) {
        return runtime.wrapFloat(expr.value);
    }

    @Override
    public Object visitStringLiteral(StringLiteral expr) {
        return runtime.wrapString(expr.value);
    }

    @Override
    public Object visitBooleanLiteral(BooleanLiteral expr) {
        return runtime.wrapBooleanValue(expr.value);
    }

    @Override
    public Object visitArrayLiteral(ArrayLiteral expr) {
        return runtime.evalArrayLiteral(expr);
    }

    @Override
    public Object visitMapLiteral(MapLiteral expr) {
        return runtime.evalMapLiteral(expr);
    }

    @Override
    public Object visitNullLiteral(NullLiteral expr) {
        return runtime.wrapNull();
    }

    @Override
    public Object visitArrayIndex(ArrayIndex expr) {
        return runtime.evalArrayIndex(expr);
    }

    @Override
    public Object visitSetIndex(SetIndex expr) {
        return runtime.evalSetIndex(expr);
    }

    @Override
    public Object visitCompoundSetExpr(CompoundSetExpr expr) {
        return runtime.evalCompoundSetExpr(expr);
    }

    @Override
    public Object visitVariableExpr(VariableExpr expr) {
        String lookupName = expr.name;
        int ltIndex = lookupName.indexOf('<');
        if (ltIndex != -1) {
            lookupName = lookupName.substring(0, ltIndex);
        }
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, lookupName);
        }
        return environment.get(lookupName);
    }

    @Override
    public Object visitAdd(Add expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.callBinaryMethod(left, "_add", right);
    }

    @Override
    public Object visitSub(Sub expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.callBinaryMethod(left, "_sub", right);
    }

    @Override
    public Object visitMultiply(Multiply expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.callBinaryMethod(left, "_mul", right);
    }

    @Override
    public Object visitDivide(Divide expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.callBinaryMethod(left, "_div", right);
    }

    @Override
    public Object visitModulo(Modulo expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.callBinaryMethod(left, "_mod", right);
    }

    @Override
    public Object visitNegative(Negative expr) {
        Object val = eval(expr.expr);
        return runtime.callUnaryMethod(val, "_neg");
    }

    @Override
    public Object visitEquality(Equality expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.wrapBooleanValue(runtime.valuesEqual(left, right));
    }

    @Override
    public Object visitInequality(Inequality expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.wrapBooleanValue(!runtime.valuesEqual(left, right));
    }

    @Override
    public Object visitGreater(Greater expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.wrapBooleanValue(runtime.requireBooleanResult(runtime.callBinaryMethod(left, "_gt", right), "Greater than"));
    }

    @Override
    public Object visitGreaterEqual(GreaterEqual expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.wrapBooleanValue(runtime.requireBooleanResult(runtime.callBinaryMethod(left, "_gte", right), "Greater than or equal"));
    }

    @Override
    public Object visitLess(Less expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.wrapBooleanValue(runtime.requireBooleanResult(runtime.callBinaryMethod(left, "_lt", right), "Less than"));
    }

    @Override
    public Object visitLessEqual(LessEqual expr) {
        Object left = eval(expr.left);
        Object right = eval(expr.right);
        return runtime.wrapBooleanValue(runtime.requireBooleanResult(runtime.callBinaryMethod(left, "_lte", right), "Less than or equal"));
    }

    @Override
    public Object visitAnd(And expr) {
        Object left = runtime.coerceBoolean(eval(expr.left));
        if (!runtime.isTrue(left)) {
            return left;
        }
        Object right = runtime.coerceBoolean(eval(expr.right));
        return runtime.callBinaryMethod(left, "_and", right);
    }

    @Override
    public Object visitOr(Or expr) {
        Object left = runtime.coerceBoolean(eval(expr.left));
        if (runtime.isTrue(left)) {
            return left;
        }
        Object right = runtime.coerceBoolean(eval(expr.right));
        return runtime.callBinaryMethod(left, "_or", right);
    }

    @Override
    public Object visitNot(Not expr) {
        Object val = runtime.coerceBoolean(eval(expr.expr));
        return runtime.callUnaryMethod(val, "_not");
    }

    @Override
    public Object visitFunctionLiteralExpr(FunctionLiteralExpr expr) {
        return new BumpFunction(functionClass, null, expr, environment, false);
    }

    @Override
    public Object visitCallExpr(CallExpr expr) {
        return runtime.evalCallExpr(expr);
    }

    @Override
    public Object visitGetExpr(GetExpr expr) {
        return runtime.evalGetExpr(expr);
    }

    @Override
    public Object visitSetExpr(SetExpr expr) {
        return runtime.evalSetExpr(expr);
    }

    @Override
    public Object visitSuperExpr(SuperExpr expr) {
        return runtime.evalSuperExpr(expr);
    }

    @Override
    public void visitBlockStmt(BlockStmt stmt) {
        Environment previous = this.environment;
        try {
            this.environment = new Environment(this.environment);
            executeBlock(stmt.statements, this.environment);
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public void visitSwitchStmt(SwitchStmt stmt) {
        Object value = eval(stmt.expression);
        boolean matched = false;
        EnumValueInstance enumValue = value instanceof EnumValueInstance enumInstance ? enumInstance : null;
        for (SwitchCase switchCase : stmt.cases) {
            boolean caseMatchedNow = false;
            String bindingName = null;
            Object bindingValue = null;
            if (!matched) {
                if (switchCase.isDefault) {
                    matched = true;
                    caseMatchedNow = true;
                } else {
                    if (enumValue != null) {
                        for (Expr label : switchCase.labels) {
                            EnumCaseRuntimeMatch enumMatch = matchEnumCaseLabel(enumValue, label);
                            if (enumMatch.matched()) {
                                matched = true;
                                caseMatchedNow = true;
                                bindingName = enumMatch.bindingName();
                                bindingValue = enumMatch.bindingValue();
                                break;
                            }
                        }
                    } else {
                        for (Expr label : switchCase.labels) {
                            if (runtime.valuesEqual(value, eval(label))) {
                                matched = true;
                                caseMatchedNow = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (matched) {
                try {
                    if (caseMatchedNow && bindingName != null) {
                        Environment previous = environment;
                        try {
                            environment = new Environment(environment);
                            environment.define(bindingName, bindingValue);
                            for (Stmt bodyStmt : switchCase.body) {
                                execute(bodyStmt);
                            }
                        } finally {
                            environment = previous;
                        }
                    } else {
                        for (Stmt bodyStmt : switchCase.body) {
                            execute(bodyStmt);
                        }
                    }
                } catch (BreakException ignored) {
                    return;
                }
            }
        }
    }

    private EnumCaseRuntimeMatch matchEnumCaseLabel(EnumValueInstance enumValue, Expr label) {
        String enumTypeName = null;
        String variantName = null;
        String bindingName = null;

        if (label instanceof VariableExpr variableExpr) {
            variantName = variableExpr.name;
        } else if (label instanceof GetExpr getExpr && getExpr.object instanceof VariableExpr enumExpr) {
            enumTypeName = enumExpr.name;
            variantName = getExpr.name;
        } else if (label instanceof CallExpr callExpr) {
            if (callExpr.callee instanceof VariableExpr variableExpr) {
                variantName = variableExpr.name;
            } else if (callExpr.callee instanceof GetExpr getExpr && getExpr.object instanceof VariableExpr enumExpr) {
                enumTypeName = enumExpr.name;
                variantName = getExpr.name;
            } else {
                return new EnumCaseRuntimeMatch(false, null, null);
            }
            if (callExpr.arguments.size() == 1 && callExpr.arguments.get(0) instanceof VariableExpr bindingVar) {
                bindingName = bindingVar.name;
            } else if (!callExpr.arguments.isEmpty()) {
                return new EnumCaseRuntimeMatch(false, null, null);
            }
        } else {
            return new EnumCaseRuntimeMatch(false, null, null);
        }

        if (variantName == null) {
            return new EnumCaseRuntimeMatch(false, null, null);
        }
        if (enumTypeName != null && !rawTypeName(enumTypeName).equals(enumValue.getRuntimeClass().name)) {
            return new EnumCaseRuntimeMatch(false, null, null);
        }
        if (!variantName.equals(enumValue.variant())) {
            return new EnumCaseRuntimeMatch(false, null, null);
        }
        if (bindingName != null) {
            return new EnumCaseRuntimeMatch(true, bindingName, enumValue.get("value"));
        }
        return new EnumCaseRuntimeMatch(true, null, null);
    }

    public void executeBlock(List<Stmt> statements, Environment env) {
        Environment previous = this.environment;
        try {
            this.environment = env;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public void visitWhileStmt(WhileStmt stmt) {
        while (runtime.isTrue(eval(stmt.condition))) {
            try {
                execute(stmt.block);
            } catch (ContinueException ignored) {
                continue;
            } catch (BreakException ignored) {
                break;
            }
        }
    }

    @Override
    public void visitForStmt(ForStmt stmt) {
        Environment previous = this.environment;
        try {
            this.environment = new Environment(this.environment);
            if (stmt.initializer != null) {
                execute(stmt.initializer);
            }
            while (stmt.condition == null || runtime.isTrue(eval(stmt.condition))) {
                try {
                    execute(stmt.body);
                } catch (ContinueException ignored) {
                    if (stmt.increment != null) {
                        execute(stmt.increment);
                    }
                    continue;
                } catch (BreakException ignored) {
                    break;
                }
                if (stmt.increment != null) {
                    execute(stmt.increment);
                }
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public void visitIfStmt(IfStmt stmt) {
        if (runtime.isTrue(eval(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
    }

    @Override
    public void visitVariableDeclarationStmt(VariableDeclarationStmt stmt) {
        if (hoistedFunctionDeclarations.remove(stmt)) {
            return;
        }
        Object value = stmt.value == null ? runtime.wrapNull() : eval(stmt.value);
        if (value instanceof BumpFunction function && function.name == null) {
            value = new BumpFunction(function.getRuntimeClass(), stmt.name, function.declaration, function.closure, function.isInitializer);
        }
        BumpClass declaredType = runtimeTypeFromSemantic(stmt.getResolvedType());
        if (declaredType == null && stmt.typeName != null) {
            declaredType = environment.getType(rawTypeName(stmt.typeName));
        }
        if ("Function".equals(stmt.typeName) && stmt.value instanceof FunctionLiteralExpr && environment.containsLocal(stmt.name)) {
            Object existing = environment.getLocal(stmt.name);
            if (existing instanceof OverloadedFunction overloaded) {
                environment.replaceLocal(stmt.name, overloaded.append((BumpCallable) value));
                return;
            }
            if (existing instanceof BumpCallable callable) {
                environment.replaceLocal(stmt.name, new OverloadedFunction(functionClass, stmt.name, List.of(callable, (BumpCallable) value)));
                return;
            }
        }
        environment.define(stmt.name, value, declaredType);
    }

    @Override
    public void visitReturnStmt(ReturnStmt stmt) {
        Object value = runtime.wrapNull();
        if (stmt.value != null) value = eval(stmt.value);
        throw new ReturnException(value);
    }

    @Override
    public void visitBreakStmt(BreakStmt stmt) {
        throw new BreakException();
    }

    @Override
    public void visitContinueStmt(ContinueStmt stmt) {
        throw new ContinueException();
    }

    @Override
    public void visitTryStmt(TryStmt stmt) {
        try {
            execute(stmt.tryBlock);
        } catch (ThrownException thrown) {
            if (stmt.catchBlock == null || !runtime.matchesCatch(stmt, thrown.exception)) {
                throw thrown;
            }
            runtime.executeCatch(stmt, thrown.exception);
        } finally {
            if (stmt.finallyBlock != null) {
                execute(stmt.finallyBlock);
            }
        }
    }

    @Override
    public void visitClassStmt(ClassStmt stmt) {
        environment.define(stmt.name, null);

        BumpClass superclass = objectClass;
        Environment methodClosure = environment;
        if (stmt.superclassName != null) {
            Object superclassValue = environment.get(rawTypeName(stmt.superclassName));
            if (!(superclassValue instanceof BumpClass bumpClass)) {
                throw BumpException.internal("Resolver accepted a non-class superclass: " + stmt.superclassName);
            }

            superclass = bumpClass;
            methodClosure = new Environment(environment);
            methodClosure.define("super", superclass);
        }
        List<BumpClass> interfaces = new ArrayList<>();
        for (String interfaceName : stmt.interfaceNames) {
            Object interfaceValue = environment.get(rawTypeName(interfaceName));
            if (!(interfaceValue instanceof BumpClass interfaceClass)) {
                throw BumpException.internal("Resolver accepted a non-class interface: " + interfaceName);
            }
            interfaces.add(interfaceClass);
        }


        Map<String, List<BumpMethod>> methods = new HashMap<>();

        for (VariableDeclarationStmt method : stmt.methods) {
            FunctionLiteralExpr functionLiteral = (FunctionLiteralExpr) method.value;
            if (functionLiteral.isAbstractMethod) {
                continue;
            }
            BumpFunction function = new BumpFunction(functionClass, method.name, functionLiteral, methodClosure, method.name.equals("init"));
            methods.computeIfAbsent(method.name, key -> new ArrayList<>()).add(function);
        }

        Map<String, BumpClass> fields = new HashMap<>();
        Map<String, Expr> fieldInitializers = new HashMap<>();
        List<String> typeParamNames = stmt.typeParameters.stream().map(tp -> tp.name).toList();
        BumpClass definedClass = new BumpClass(classClass, stmt.name, fields, fieldInitializers, methods, superclass, interfaces, methodClosure, false, stmt.isAbstract, typeParamNames);
        for (VariableDeclarationStmt field : stmt.fields) {
            BumpClass fieldType = runtimeTypeFromSemantic(field.getResolvedType());
            if (fieldType != null && field.getResolvedType() != null && field.getResolvedType().name().equals(stmt.name)) {
                fieldType = definedClass;
            }
            if (fieldType == null) {
                fieldType = rawTypeName(field.typeName).equals(stmt.name)
                        ? definedClass
                        : environment.getType(rawTypeName(field.typeName));
            }
            fields.put(field.name, fieldType);
            if (field.value != null) {
                fieldInitializers.put(field.name, field.value);
            }
        }
        environment.assign(stmt.name, definedClass);
    }

    @Override
    public void visitEnumStmt(EnumStmt stmt) {
        environment.define(stmt.name, null);

        Map<String, BumpClass> enumFields = new HashMap<>();
        enumFields.put("name", stringClass());
        enumFields.put("ordinal", integerClass());
        enumFields.put("value", objectClass);
        for (EnumVariant variant : stmt.variants) {
            if (variant.payloadName != null) {
                enumFields.put(variant.payloadName, objectClass);
            }
        }

        BumpClass enumClass = new BumpClass(
                classClass,
                stmt.name,
                enumFields,
                new HashMap<>(),
                new HashMap<>(),
                objectClass,
                List.of(),
                environment,
                false,
                false,
                stmt.typeParameters.stream().map(typeParameter -> typeParameter.name).toList()
        );
        enumClass.markAsEnum();
        environment.assign(stmt.name, enumClass);

        for (int i = 0; i < stmt.variants.size(); i++) {
            EnumVariant variant = stmt.variants.get(i);
            if (variant.payloadName != null) {
                enumClass.defineEnumConstructor(variant.name, new EnumVariantConstructor(enumClass, variant.name, i, variant.payloadName));
            } else {
                Object attachedValue = variant.attachedValue == null ? wrapNull() : eval(variant.attachedValue);
                EnumValueInstance enumValue = new EnumValueInstance(enumClass, variant.name, i, wrapString(variant.name), wrapInteger(i), attachedValue);
                enumClass.defineEnumValue(variant.name, enumValue);
            }
        }
    }

    @Override
    public void visitExtendStmt(ExtendStmt stmt) {
        Object classValue = environment.get(stmt.className);
        if (!(classValue instanceof BumpClass targetClass)) {
            throw BumpException.internal("Resolver accepted a non-class extension target: " + stmt.className);
        }

        Environment methodClosure = environment;
        BumpClass superclass = targetClass.getSuperclass();
        if (superclass != null) {
            methodClosure = new Environment(environment);
            methodClosure.define("super", superclass);
        }
        for (String typeParameterName : targetClass.getTypeParameterNames()) {
            if (!methodClosure.containsLocal(typeParameterName)) {
                methodClosure.define(typeParameterName, objectClass);
            }
        }

        for (VariableDeclarationStmt method : stmt.methods) {
            BumpFunction function = new BumpFunction(
                    functionClass,
                    method.name,
                    (FunctionLiteralExpr) method.value,
                    methodClosure,
                    method.name.equals("init")
            );
            targetClass.defineMethod(method.name, function);
        }
    }

    @Override
    public void visitExpressionStmt(ExpressionStmt stmt) {
        eval(stmt.expr);
    }

    @Override
    public void visitJavaBlockStmt(JavaBlockStmt stmt) {
        JavaBlockRuntime.execute(stmt, this, environment);
    }

    public NullClass nullClass() {
        return (NullClass) globals.get("Null");
    }

    public FunctionClass functionClass() {
        return functionClass;
    }

    public BumpClass exceptionClass() {
        return exceptionClass;
    }

    private StringClass stringClass() {
        return (StringClass) globals.get("String");
    }

    private IntegerClass integerClass() {
        return (IntegerClass) globals.get("Integer");
    }

    public FloatClass floatClass() {
        return (FloatClass) globals.get("Float");
    }

    private ArrayClass arrayClass() {
        return (ArrayClass) globals.get("Array");
    }

    private BumpClass runtimeTypeFromSemantic(SemanticType semanticType) {
        if (semanticType == null) {
            return null;
        }
        if (semanticType.isTypeParameter()) {
            SemanticType upperBound = semanticType.upperBound();
            return upperBound == null ? null : environment.getType(rawTypeName(upperBound.name()));
        }
        return environment.getType(rawTypeName(semanticType.name()));
    }

    public static String rawTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart < 0 ? typeName : typeName.substring(0, genericStart);
    }

    public BoolInstance wrapBooleanValue(boolean value) {
        return runtime.wrapBooleanValue(value);
    }

    public StringInstance wrapString(String value) {
        return runtime.wrapString(value);
    }

    public CharInstance wrapChar(char value) {
        return runtime.wrapChar(value);
    }

    public IntegerInstance wrapInteger(int value) {
        return runtime.wrapInteger(value);
    }

    public FloatInstance wrapFloat(double value) {
        return runtime.wrapFloat(value);
    }

    public ArrayInstance wrapArray(List<Object> values) {
        return runtime.wrapArray(values);
    }

    public NullInstance wrapNull() {
        return runtime.wrapNull();
    }

    public IntegerInstance wrapIntegerValue(int value) {
        return runtime.wrapIntegerValue(value);
    }

    public void throwException(Object value) {
        runtime.throwException(value);
    }

    public BumpException uncaughtThrownException(ThrownException thrown) {
        return runtime.uncaughtThrownException(thrown);
    }

    public boolean valuesEqual(Object left, Object right) {
        return runtime.valuesEqual(left, right);
    }

    public BumpInstance runtimeErrorAsException(BumpRuntimeError error) {
        return runtime.runtimeErrorAsException(error);
    }

    public int requireIndexValue(Object value) {
        return runtime.requireIndexValue(value);
    }

    public Object callBinaryMethod(Object receiver, String methodName, Object argument) {
        return runtime.callBinaryMethod(receiver, methodName, argument);
    }
}
