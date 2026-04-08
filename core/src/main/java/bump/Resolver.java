package bump;

import ast.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Resolver implements Visitor<Void> {
    final Interpreter interpreter;
    final ImportVisibility importVisibility;
    final Deque<Map<String, Boolean>> scopes = new ArrayDeque<>();
    final Deque<Map<String, SymbolInfo>> symbols = new ArrayDeque<>();
    final Deque<Map<String, SemanticType>> typeParameterScopes = new ArrayDeque<>();
    FunctionType currentFunction = FunctionType.NONE;
    ClassType currentClass = ClassType.NONE;
    SemanticType currentReturnType = null;
    ClassInfo currentClassInfo = null;
    int breakableDepth = 0;
    int continueDepth = 0;
    SemanticType objectType;
    SemanticType comparableType;
    SemanticType classType;
    SemanticType functionType;
    SemanticType arrayType;
    SemanticType boolType;
    SemanticType charType;
    SemanticType nullType;
    SemanticType stringType;
    SemanticType integerType;
    SemanticType floatType;
    SemanticType dateTimeType;
    SemanticType mapType;
    SemanticType fileType;
    SemanticType exceptionType;
    final ResolverTypeSystem typeSystem = new ResolverTypeSystem(this);
    final ResolverTypeNameResolver typeNameResolver = new ResolverTypeNameResolver(this);
    final ResolverExpressionAnalyzer expressionAnalyzer = new ResolverExpressionAnalyzer(this);
    private final ResolverStatementAnalyzer statementAnalyzer = new ResolverStatementAnalyzer(this);
    private final ResolverReturnAnalysis returnAnalysis = new ResolverReturnAnalysis();
    private final Set<FunctionLiteralExpr> predeclaredFunctionLiterals = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private int activeLine = -1;
    private List<BumpException> recoveryErrors = null;

    public Resolver(Interpreter interpreter) {
        this(interpreter, null);
    }

    public Resolver(Interpreter interpreter, ImportVisibility importVisibility) {
        this.interpreter = interpreter;
        this.importVisibility = importVisibility;
    }

    public void resolve(List<Stmt> program) {
        resolveInternal(program, false);
    }

    public List<BumpException> resolveWithRecovery(List<Stmt> program) {
        recoveryErrors = new ArrayList<>();
        try {
            resolveInternal(program, true);
            return List.copyOf(recoveryErrors);
        } finally {
            recoveryErrors = null;
        }
    }

    private void resolveInternal(List<Stmt> program, boolean recover) {
        beginScope();
        try {
            defineBuiltInSymbols();
            for (int i = 0; i < program.size(); i++) {
                Stmt statement = program.get(i);
                int previousLine = activeLine;
                if (statement.getLine() > 0) {
                    activeLine = statement.getLine();
                }
                try {
                    ensureFunctionOverloadsDeclared(program, i);
                    resolveStatement(statement);
                } catch (BumpException error) {
                    if (!recover) {
                        throw error;
                    }
                    BumpException normalized = error.withFallbackLocation(
                            statement.getLine(),
                            statement.getColumn(),
                            statement.getLength(),
                            statement.describeLocation()
                    );
                    recoveryErrors.add(normalized);
                    i = skipStatementsOnSameLine(program, i, normalized.getLine());
                } finally {
                    activeLine = previousLine;
                }
            }
        } finally {
            endScope();
        }
    }

    private int skipStatementsOnSameLine(List<Stmt> statements, int index, Integer line) {
        if (line == null) {
            return index;
        }
        int cursor = index;
        while (cursor + 1 < statements.size()) {
            Stmt next = statements.get(cursor + 1);
            if (next.getLine() != line) {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    void resolveStatement(Stmt stmt) {
        int previousLine = activeLine;
        if (stmt.getLine() > 0) {
            activeLine = stmt.getLine();
        }
        try {
            stmt.accept(this);
        } catch (BumpException error) {
            throw error.withFallbackLocation(stmt.getLine(), stmt.getColumn(), stmt.getLength(), stmt.describeLocation());
        } finally {
            activeLine = previousLine;
        }
    }

    void resolveExpression(Expr expr) {
        int previousLine = activeLine;
        if (expr.getLine() > 0) {
            activeLine = expr.getLine();
        }
        try {
            expressionAnalyzer.analyze(expr);
        } catch (BumpException error) {
            throw error.withFallbackLocation(expr.getLine(), expr.getColumn(), expr.getLength(), expr.describeLocation());
        } finally {
            activeLine = previousLine;
        }
    }

    void resolveFunction(FunctionLiteralExpr function, FunctionType type) {
        if (function.isAbstractMethod) {
            resolveFunctionSignature(function);
            return;
        }
        FunctionType enclosingFunction = currentFunction;
        SemanticType enclosingReturnType = currentReturnType;
        currentFunction = type;
        try {
            resolveFunctionSignature(function);
            currentReturnType = function.getResolvedReturnType();
            for (Parameter parameter : function.parameters) {
                // Signatures are resolved before body resolution so call sites can use them.
            }
            beginTypeParameterScope(function.typeParameters);
            beginScope();
            try {
                for (Parameter parameter : function.parameters) {
                    declare(parameter.name);
                    setCurrentSymbol(parameter.name, SymbolKind.VARIABLE, parameter.getResolvedType(), null);
                    define(parameter.name);
                }
                for (int i = 0; i < function.body.size(); i++) {
                    Stmt bodyStmt = function.body.get(i);
                    try {
                        ensureFunctionOverloadsDeclared(function.body, i);
                        resolveStatement(bodyStmt);
                    } catch (BumpException error) {
                        if (recoveryErrors == null) {
                            throw error;
                        }
                        BumpException normalized = error.withFallbackLocation(
                                bodyStmt.getLine(),
                                bodyStmt.getColumn(),
                                bodyStmt.getLength(),
                                bodyStmt.describeLocation()
                        );
                        recoveryErrors.add(normalized);
                        i = skipStatementsOnSameLine(function.body, i, normalized.getLine());
                    }
                }
                if (function.getResolvedReturnType() == null) {
                    try {
                        SemanticType inferredReturnType = inferReturnType(function.body);
                        function.resolveReturnType(inferredReturnType);
                        currentReturnType = inferredReturnType;
                    } catch (BumpException error) {
                        if (recoveryErrors == null) {
                            throw error;
                        }
                        recoveryErrors.add(error.withFallbackLocation(
                                function.getLine(),
                                function.getColumn(),
                                function.getLength(),
                                function.describeLocation()
                        ));
                        currentReturnType = nullType;
                    }
                }
                if (type != FunctionType.INITIALIZER
                        && currentReturnType != null
                        && currentReturnType != nullType
                        && !returnAnalysis.definitelyReturns(function.body)) {
                    BumpException missingReturn = BumpException.semantic(
                            "Function with return type " + currentReturnType.name() + " must return a value on all paths."
                    ).withFallbackLocation(function.getLine(), function.getColumn(), function.getLength(), function.describeLocation());
                    if (recoveryErrors == null) {
                        throw missingReturn;
                    }
                    recoveryErrors.add(missingReturn);
                }
            } finally {
                endScope();
                endTypeParameterScope(function.typeParameters);
            }
        } finally {
            currentFunction = enclosingFunction;
            currentReturnType = enclosingReturnType;
        }
    }

    private SemanticType inferReturnType(List<Stmt> statements) {
        List<ReturnStmt> returns = new ArrayList<>();
        collectReturnStatements(statements, returns);
        if (returns.isEmpty()) {
            return nullType;
        }

        SemanticType inferred = nullType;
        for (ReturnStmt returnStmt : returns) {
            SemanticType candidate = returnStmt.value == null ? nullType : expressionAnalyzer.analyze(returnStmt.value);
            if (candidate == null || candidate == nullType) {
                continue;
            }
            if (inferred == nullType) {
                inferred = candidate;
                continue;
            }
            if (inferred.isAssignableFrom(candidate)) {
                continue;
            }
            if (candidate.isAssignableFrom(inferred)) {
                inferred = candidate;
                continue;
            }
            throw BumpException.semantic("Lambda/function return types are incompatible: " + inferred.displayName()
                    + " and " + candidate.displayName() + ".");
        }
        return inferred;
    }

    private void collectReturnStatements(List<Stmt> statements, List<ReturnStmt> returns) {
        for (Stmt statement : statements) {
            collectReturnStatements(statement, returns);
        }
    }

    private void collectReturnStatements(Stmt statement, List<ReturnStmt> returns) {
        if (statement instanceof ReturnStmt returnStmt) {
            returns.add(returnStmt);
            return;
        }
        if (statement instanceof BlockStmt blockStmt) {
            collectReturnStatements(blockStmt.statements, returns);
            return;
        }
        if (statement instanceof IfStmt ifStmt) {
            collectReturnStatements(ifStmt.thenBranch, returns);
            if (ifStmt.elseBranch != null) {
                collectReturnStatements(ifStmt.elseBranch, returns);
            }
            return;
        }
        if (statement instanceof WhileStmt whileStmt) {
            collectReturnStatements(whileStmt.block, returns);
            return;
        }
        if (statement instanceof ForStmt forStmt) {
            if (forStmt.initializer != null) {
                collectReturnStatements(forStmt.initializer, returns);
            }
            if (forStmt.increment != null) {
                collectReturnStatements(forStmt.increment, returns);
            }
            collectReturnStatements(forStmt.body, returns);
            return;
        }
        if (statement instanceof SwitchStmt switchStmt) {
            for (SwitchCase switchCase : switchStmt.cases) {
                collectReturnStatements(switchCase.body, returns);
            }
            return;
        }
        if (statement instanceof TryStmt tryStmt) {
            collectReturnStatements(tryStmt.tryBlock, returns);
            if (tryStmt.catchBlock != null) {
                collectReturnStatements(tryStmt.catchBlock, returns);
            }
            if (tryStmt.finallyBlock != null) {
                collectReturnStatements(tryStmt.finallyBlock, returns);
            }
        }
    }

    private void resolveFunctionSignature(FunctionLiteralExpr function) {
        beginTypeParameterScope(function.typeParameters);
        try {
            if (function.returnTypeName != null && function.getResolvedReturnType() == null) {
                function.resolveReturnType(resolveTypeName(function.returnTypeName, "Type '" + function.returnTypeName + "' is not a class."));
            }
            for (Parameter parameter : function.parameters) {
                if (parameter.getResolvedType() != null) {
                    continue;
                }
                try {
                    parameter.resolveType(resolveTypeName(parameter.typeName, "Type '" + parameter.typeName + "' is not a class."));
                } catch (BumpException error) {
                    throw error.withFallbackLocation(parameter.getLine(), parameter.getColumn(), parameter.getLength(), parameter.toString());
                }
            }
        } finally {
            endTypeParameterScope(function.typeParameters);
        }
    }

    void beginScope() {
        scopes.push(new HashMap<>());
        symbols.push(new HashMap<>());
    }

    void endScope() {
        scopes.pop();
        symbols.pop();
    }

    void beginTypeParameterScope(List<TypeParameter> typeParameters) {
        if (typeParameters.isEmpty()) {
            return;
        }
        Map<String, SemanticType> scope = new HashMap<>();
        typeParameterScopes.push(scope);
        for (TypeParameter typeParameter : typeParameters) {
            if (scope.containsKey(typeParameter.name)) {
                throw BumpException.semantic("Type parameter '" + typeParameter.name + "' is already defined in this scope.");
            }
            SemanticType upperBound = objectType;
            if (typeParameter.boundName != null) {
                try {
                    upperBound = resolveTypeName(typeParameter.boundName, "Type '" + typeParameter.boundName + "' is not a class.");
                } catch (BumpException error) {
                    throw error.withFallbackLocation(typeParameter.getLine(), typeParameter.getColumn(), typeParameter.getLength(), typeParameter.name);
                }
            }
            typeParameter.resolveBoundType(upperBound);
            scope.put(typeParameter.name, SemanticType.typeParameter(typeParameter.name, upperBound));
        }
    }

    void endTypeParameterScope(List<TypeParameter> typeParameters) {
        if (typeParameters.isEmpty()) {
            return;
        }
        typeParameterScopes.pop();
    }

    void beginSemanticTypeParameterScope(List<SemanticType> typeParameters) {
        if (typeParameters.isEmpty()) {
            return;
        }
        Map<String, SemanticType> scope = new HashMap<>();
        typeParameterScopes.push(scope);
        for (SemanticType typeParameter : typeParameters) {
            scope.put(typeParameter.name(), typeParameter);
        }
    }

    void endSemanticTypeParameterScope(List<SemanticType> typeParameters) {
        if (typeParameters.isEmpty()) {
            return;
        }
        typeParameterScopes.pop();
    }

    void ensureFunctionOverloadsDeclared(List<Stmt> statements, int statementIndex) {
        if (statementIndex < 0 || statementIndex >= statements.size()) {
            return;
        }

        for (int i = statementIndex; i < statements.size(); i++) {
            Stmt candidate = statements.get(i);
            if (candidate instanceof VariableDeclarationStmt declaration
                    && "Function".equals(declaration.typeName)
                    && declaration.value instanceof FunctionLiteralExpr functionLiteral
                    && !predeclaredFunctionLiterals.contains(functionLiteral)) {
                try {
                    declareFunctionOverload(declaration.name, functionLiteral);
                    predeclaredFunctionLiterals.add(functionLiteral);
                } catch (BumpException error) {
                    if (shouldDeferFunctionPredeclaration(error)) {
                        continue;
                    }
                    throw error;
                }
            }
        }
    }

    private boolean shouldDeferFunctionPredeclaration(BumpException error) {
        if (!"Semantic error".equals(error.getCategory())) {
            return false;
        }
        String detail = error.getDetail();
        return detail != null && detail.startsWith("Type '") && detail.endsWith("' is not a class.");
    }

    private void defineBuiltInSymbols() {
        Map<String, SemanticType> builtinTypes = new HashMap<>();
        Map<String, ClassInfo> builtinClasses = new HashMap<>();

        for (Builtins.ClassDefinition definition : Builtins.classDefinitions()) {
            SemanticType superclassType = definition.superclassName() == null
                    ? null
                    : builtinTypes.get(definition.superclassName());
            List<SemanticType> interfaceTypes = definition.interfaceNames().stream()
                    .map(builtinTypes::get)
                    .toList();
            SemanticType semanticType = new SemanticType(definition.name(), true, definition.extendable(), superclassType, interfaceTypes);
            ClassInfo superclass = definition.superclassName() == null
                    ? null
                    : builtinClasses.get(definition.superclassName());
            List<ClassInfo> interfaces = definition.interfaceNames().stream()
                    .map(builtinClasses::get)
                    .toList();
            boolean abstractClass = Builtins.COMPARABLE.equals(definition.name());
            ClassInfo classInfo = new ClassInfo(definition.name(), true, definition.extendable(), abstractClass, List.of(), superclass, interfaces, semanticType);
            builtinTypes.put(definition.name(), semanticType);
            builtinClasses.put(definition.name(), classInfo);
            defineSymbol(definition.name(), SymbolKind.CLASS, semanticType, classInfo);
        }

        objectType = builtinTypes.get(Builtins.OBJECT);
        comparableType = builtinTypes.get(Builtins.COMPARABLE);
        classType = builtinTypes.get(Builtins.CLASS);
        functionType = builtinTypes.get(Builtins.FUNCTION);
        arrayType = builtinTypes.get(Builtins.ARRAY);
        boolType = builtinTypes.get(Builtins.BOOL);
        charType = builtinTypes.get(Builtins.CHAR);
        nullType = builtinTypes.get(Builtins.NULL);
        stringType = builtinTypes.get(Builtins.STRING);
        integerType = builtinTypes.get(Builtins.INTEGER);
        floatType = builtinTypes.get(Builtins.FLOAT);
        dateTimeType = builtinTypes.get(Builtins.DATETIME);
        mapType = builtinTypes.get(Builtins.MAP);
        fileType = builtinTypes.get(Builtins.FILE);
        exceptionType = builtinTypes.get(Builtins.EXCEPTION);

        installBuiltInMembers(builtinClasses);

        for (Builtins.FunctionDefinition definition : Builtins.functionDefinitions()) {
            SemanticType returnType = builtinTypes.get(definition.returnTypeName());
            List<SemanticType> parameterTypes = definition.parameterTypeNames().stream()
                    .map(builtinTypes::get)
                    .toList();
            defineSymbol(
                    definition.name(),
                    SymbolKind.FUNCTION,
                    new SemanticType(Builtins.FUNCTION, false, functionType, returnType, parameterTypes),
                    null
            );
        }
    }

    private void installBuiltInMembers(Map<String, ClassInfo> builtinClasses) {
        ClassInfo objectClass = builtinClasses.get(Builtins.OBJECT);
        ClassInfo comparableClass = builtinClasses.get(Builtins.COMPARABLE);
        ClassInfo arrayClass = builtinClasses.get(Builtins.ARRAY);
        ClassInfo boolClass = builtinClasses.get(Builtins.BOOL);
        ClassInfo charClass = builtinClasses.get(Builtins.CHAR);
        ClassInfo stringClass = builtinClasses.get(Builtins.STRING);
        ClassInfo integerClass = builtinClasses.get(Builtins.INTEGER);
        ClassInfo floatClass = builtinClasses.get(Builtins.FLOAT);
        ClassInfo dateTimeClass = builtinClasses.get(Builtins.DATETIME);
        ClassInfo mapClass = builtinClasses.get(Builtins.MAP);
        ClassInfo fileClass = builtinClasses.get(Builtins.FILE);
        ClassInfo exceptionClass = builtinClasses.get(Builtins.EXCEPTION);

        objectClass.methods.put("_eq", List.of(signature(boolType, objectType)));
        objectClass.methods.put("string", List.of(signature(stringType)));
        objectClass.methods.put("class_name", List.of(signature(stringType)));
        objectClass.methods.put("_gt", List.of(signature(boolType, comparableType)));
        objectClass.methods.put("_gte", List.of(signature(boolType, comparableType)));
        objectClass.methods.put("_lt", List.of(signature(boolType, comparableType)));
        objectClass.methods.put("_lte", List.of(signature(boolType, comparableType)));

        comparableClass.methods.put("_eq", List.of(signature(boolType, comparableType)));
        comparableClass.methods.put("_gt", List.of(signature(boolType, comparableType)));
        comparableClass.methods.put("_gte", List.of(signature(boolType, comparableType)));
        comparableClass.methods.put("_lt", List.of(signature(boolType, comparableType)));
        comparableClass.methods.put("_lte", List.of(signature(boolType, comparableType)));
        comparableClass.abstractMethods.put("_eq", List.of(signature(boolType, comparableType)));
        comparableClass.abstractMethods.put("_gt", List.of(signature(boolType, comparableType)));
        comparableClass.abstractMethods.put("_gte", List.of(signature(boolType, comparableType)));
        comparableClass.abstractMethods.put("_lt", List.of(signature(boolType, comparableType)));
        comparableClass.abstractMethods.put("_lte", List.of(signature(boolType, comparableType)));

        arrayClass.methods.put("length", List.of(signature(integerType)));
        arrayClass.methods.put("get", List.of(signature(objectType, integerType)));
        arrayClass.methods.put("set", List.of(signature(objectType, integerType, objectType)));
        arrayClass.methods.put("push", List.of(signature(objectType, objectType)));
        arrayClass.methods.put("pop", List.of(signature(objectType)));
        arrayClass.methods.put("insert", List.of(signature(objectType, integerType, objectType)));
        arrayClass.methods.put("remove", List.of(signature(objectType, integerType)));
        arrayClass.methods.put("clear", List.of(signature(nullType)));
        arrayClass.methods.put("copy", List.of(signature(arrayType)));
        arrayClass.methods.put("contains", List.of(signature(boolType, objectType)));
        arrayClass.methods.put("index_of", List.of(signature(integerType, objectType)));
        arrayClass.methods.put("join", List.of(signature(stringType, stringType)));
        arrayClass.methods.put("_eq", List.of(signature(boolType, objectType)));

        boolClass.methods.put("_and", List.of(signature(boolType, boolType)));
        boolClass.methods.put("_or", List.of(signature(boolType, boolType)));
        boolClass.methods.put("_not", List.of(signature(boolType)));
        boolClass.methods.put("_eq", List.of(signature(boolType, objectType)));

        charClass.methods.put("code", List.of(signature(integerType)));
        charClass.methods.put("upper", List.of(signature(charType)));
        charClass.methods.put("lower", List.of(signature(charType)));
        charClass.methods.put("is_digit", List.of(signature(boolType)));
        charClass.methods.put("is_letter", List.of(signature(boolType)));
        charClass.methods.put("is_whitespace", List.of(signature(boolType)));
        charClass.methods.put("_add", List.of(signature(stringType, objectType)));
        charClass.methods.put("_eq", List.of(signature(boolType, objectType)));

        stringClass.methods.put("length", List.of(signature(integerType)));
        stringClass.methods.put("char_at", List.of(signature(charType, integerType)));
        stringClass.methods.put("substring", List.of(signature(stringType, integerType, integerType)));
        stringClass.methods.put("slice", List.of(signature(stringType, integerType, integerType)));
        stringClass.methods.put("index_of", List.of(signature(integerType, objectType)));
        stringClass.methods.put("last_index_of", List.of(signature(integerType, objectType)));
        stringClass.methods.put("contains", List.of(signature(boolType, objectType)));
        stringClass.methods.put("starts_with", List.of(signature(boolType, objectType)));
        stringClass.methods.put("ends_with", List.of(signature(boolType, objectType)));
        stringClass.methods.put("count", List.of(signature(integerType, objectType)));
        stringClass.methods.put("trim", List.of(signature(stringType)));
        stringClass.methods.put("lower", List.of(signature(stringType)));
        stringClass.methods.put("upper", List.of(signature(stringType)));
        stringClass.methods.put("replace", List.of(signature(stringType, objectType, objectType)));
        stringClass.methods.put("repeat", List.of(signature(stringType, integerType)));
        stringClass.methods.put("split", List.of(signature(arrayType, objectType)));
        stringClass.methods.put("_add", List.of(signature(stringType, objectType)));
        stringClass.methods.put("_eq", List.of(signature(boolType, objectType)));

        integerClass.methods.put("_add", List.of(signature(integerType, integerType), signature(floatType, floatType)));
        integerClass.methods.put("_sub", List.of(signature(integerType, integerType), signature(floatType, floatType)));
        integerClass.methods.put("_mul", List.of(signature(integerType, integerType), signature(floatType, floatType)));
        integerClass.methods.put("_div", List.of(signature(integerType, integerType), signature(floatType, floatType)));
        integerClass.methods.put("_mod", List.of(signature(integerType, integerType), signature(floatType, floatType)));
        integerClass.methods.put("_neg", List.of(signature(integerType)));
        integerClass.methods.put("_eq", List.of(signature(boolType, objectType)));
        integerClass.methods.put("_gt", List.of(signature(boolType, integerType), signature(boolType, floatType)));
        integerClass.methods.put("_gte", List.of(signature(boolType, integerType), signature(boolType, floatType)));
        integerClass.methods.put("_lt", List.of(signature(boolType, integerType), signature(boolType, floatType)));
        integerClass.methods.put("_lte", List.of(signature(boolType, integerType), signature(boolType, floatType)));

        floatClass.methods.put("_add", List.of(signature(floatType, integerType), signature(floatType, floatType)));
        floatClass.methods.put("_sub", List.of(signature(floatType, integerType), signature(floatType, floatType)));
        floatClass.methods.put("_mul", List.of(signature(floatType, integerType), signature(floatType, floatType)));
        floatClass.methods.put("_div", List.of(signature(floatType, integerType), signature(floatType, floatType)));
        floatClass.methods.put("_mod", List.of(signature(floatType, integerType), signature(floatType, floatType)));
        floatClass.methods.put("_neg", List.of(signature(floatType)));
        floatClass.methods.put("_eq", List.of(signature(boolType, objectType)));
        floatClass.methods.put("_gt", List.of(signature(boolType, integerType), signature(boolType, floatType)));
        floatClass.methods.put("_gte", List.of(signature(boolType, integerType), signature(boolType, floatType)));
        floatClass.methods.put("_lt", List.of(signature(boolType, integerType), signature(boolType, floatType)));
        floatClass.methods.put("_lte", List.of(signature(boolType, integerType), signature(boolType, floatType)));

        dateTimeClass.methods.put("year", List.of(signature(integerType)));
        dateTimeClass.methods.put("month", List.of(signature(integerType)));
        dateTimeClass.methods.put("day", List.of(signature(integerType)));
        dateTimeClass.methods.put("hour", List.of(signature(integerType)));
        dateTimeClass.methods.put("minute", List.of(signature(integerType)));
        dateTimeClass.methods.put("second", List.of(signature(integerType)));
        dateTimeClass.methods.put("to_string", List.of(signature(stringType)));
        dateTimeClass.methods.put("_eq", List.of(signature(boolType, objectType)));
        dateTimeClass.methods.put("_gt", List.of(signature(boolType, dateTimeType)));
        dateTimeClass.methods.put("_gte", List.of(signature(boolType, dateTimeType)));
        dateTimeClass.methods.put("_lt", List.of(signature(boolType, dateTimeType)));
        dateTimeClass.methods.put("_lte", List.of(signature(boolType, dateTimeType)));

        mapClass.methods.put("length", List.of(signature(integerType)));
        mapClass.methods.put("contains_key", List.of(signature(boolType, objectType)));
        mapClass.methods.put("get", List.of(signature(objectType, objectType)));
        mapClass.methods.put("set", List.of(signature(objectType, objectType, objectType)));
        mapClass.methods.put("remove", List.of(signature(objectType, objectType)));
        mapClass.methods.put("keys", List.of(signature(arrayType)));
        mapClass.methods.put("values", List.of(signature(arrayType)));
        mapClass.methods.put("clear", List.of(signature(nullType)));
        mapClass.methods.put("copy", List.of(signature(mapType)));
        mapClass.methods.put("_eq", List.of(signature(boolType, objectType)));

        fileClass.methods.put("open", List.of(signature(fileType)));
        fileClass.methods.put("contents", List.of(signature(stringType)));
        fileClass.methods.put("set_contents", List.of(signature(nullType, stringType)));
        fileClass.methods.put("close", List.of(signature(nullType)));
        fileClass.methods.put("create", List.of(signature(nullType)));
        fileClass.methods.put("get_path", List.of(signature(stringType)));

        exceptionClass.fields.put(Builtins.EXCEPTION_MESSAGE, stringType);
        exceptionClass.fields.put(Builtins.EXCEPTION_LINE, integerType);
        exceptionClass.fields.put(Builtins.EXCEPTION_COLUMN, integerType);
        exceptionClass.fields.put(Builtins.EXCEPTION_CONTEXT, stringType);
        exceptionClass.fields.put(Builtins.EXCEPTION_STACK_TRACE, arrayType);

        for (ClassInfo classInfo : builtinClasses.values()) {
            for (Map.Entry<String, List<SemanticType>> entry : classInfo.methods.entrySet()) {
                List<SemanticType> concrete = new ArrayList<>();
                List<SemanticType> abstracts = classInfo.abstractMethods.getOrDefault(entry.getKey(), List.of());
                for (SemanticType signature : entry.getValue()) {
                    boolean isAbstractSignature = abstracts.stream().anyMatch(abstractSig -> sameSignature(abstractSig, signature));
                    if (!isAbstractSignature) {
                        concrete.add(signature);
                    }
                }
                if (!concrete.isEmpty()) {
                    classInfo.concreteMethods.put(entry.getKey(), concrete);
                }
            }
        }
    }

    void defineSymbol(String name, SymbolKind kind, SemanticType semanticType, ClassInfo classInfo) {
        List<SemanticType> overloads = kind == SymbolKind.FUNCTION && semanticType != null ? List.of(semanticType) : List.of();
        symbols.peek().put(name, new SymbolInfo(kind, semanticType, classInfo, overloads, currentSourceFile()));
    }

    SymbolInfo lookupSymbol(String name) {
        for (Map<String, SymbolInfo> scope : symbols) {
            SymbolInfo symbol = scope.get(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    void declare(String name) {
        if (scopes.isEmpty()) {
            return;
        }
        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name)) {
            throw BumpException.semantic("Variable '" + name + "' is already defined in this scope.");
        }
        scope.put(name, false);
        defineSymbol(name, SymbolKind.VARIABLE, null, null);
    }

    void resolveAssignment(Expr expr, String name) {
        int distance = 0;
        for (Map<String, Boolean> scope : scopes) {
            if (scope.containsKey(name)) {
                if (Boolean.FALSE.equals(scope.get(name))) {
                    throw BumpException.semantic("Cannot assign to '" + name + "' before initialization.");
                }
                interpreter.resolve(expr, distance);
                return;
            }
            distance++;
        }
    }

    void define(String name) {
        if (scopes.isEmpty()) {
            return;
        }
        scopes.peek().put(name, true);
    }

    void setCurrentSymbol(String name, SymbolKind kind, SemanticType semanticType, ClassInfo classInfo) {
        List<SemanticType> overloads = kind == SymbolKind.FUNCTION && semanticType != null ? List.of(semanticType) : List.of();
        symbols.peek().put(name, new SymbolInfo(kind, semanticType, classInfo, overloads, currentSourceFile()));
    }

    SymbolInfo currentScopeSymbol(String name) {
        return symbols.peek().get(name);
    }

    void declareFunctionOverload(String name, FunctionLiteralExpr function) {
        SemanticType signature = functionValueType(function);
        String declarationSourceFile = BumpException.sourceFileForLine(function.getLine());
        if (declarationSourceFile == null) {
            declarationSourceFile = currentSourceFile();
        }
        SymbolInfo existing = currentScopeSymbol(name);
        if (existing == null) {
            scopes.peek().put(name, true);
            symbols.peek().put(name, new SymbolInfo(SymbolKind.FUNCTION, signature, null, List.of(signature), declarationSourceFile));
            return;
        }
        if (existing.kind() != SymbolKind.FUNCTION) {
            throw BumpException.semantic("Variable '" + name + "' is already defined in this scope.");
        }
        if (existing.overloads().stream().anyMatch(overload -> sameSignature(overload, signature))) {
            throw BumpException.semantic("Function '" + name + "' already has an overload with the same parameter types.");
        }
        SymbolInfo updated = existing.withOverload(signature);
        symbols.peek().put(name, new SymbolInfo(SymbolKind.FUNCTION, updated.semanticType(), null, updated.overloads(), declarationSourceFile));
    }

    SemanticType resolveDeclaredType(String typeName, String message) {
        SymbolInfo symbol = lookupSymbol(typeName);
        if (symbol == null || symbol.kind() != SymbolKind.CLASS || symbol.semanticType() == null) {
            throw BumpException.semantic(message);
        }
        ensureSymbolVisible(symbol, typeName);
        return symbol.semanticType();
    }

    SemanticType resolveTypeName(String typeName, String message) {
        return typeNameResolver.resolveTypeName(typeName, message);
    }

    SemanticType lookupTypeParameter(String name) {
        for (Map<String, SemanticType> scope : typeParameterScopes) {
            SemanticType typeParameter = scope.get(name);
            if (typeParameter != null) {
                return typeParameter;
            }
        }
        return null;
    }

    ClassInfo classInfoForType(SemanticType semanticType) {
        if (semanticType == null) {
            return null;
        }
        if (semanticType.isTypeParameter()) {
            SemanticType upperBound = semanticType.upperBound();
            if (upperBound == null) {
                return classInfoForType(objectType);
            }
            return classInfoForType(upperBound);
        }
        SymbolInfo symbol = lookupSymbol(semanticType.name());
        if (symbol == null || symbol.kind() != SymbolKind.CLASS) {
            return null;
        }
        return symbol.classInfo();
    }

    List<SemanticType> bindMethodOverloads(ClassInfo classInfo, SemanticType receiverType, String methodName) {
        List<SemanticType> overloads = classInfo.methodOverloads(methodName);
        if (classInfo.typeParameters.isEmpty()) {
            return overloads;
        }
        return overloads.stream()
                .map(overload -> typeSystem.applyClassTypeArguments(classInfo, receiverType, overload))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    SemanticType bindFieldType(ClassInfo classInfo, SemanticType receiverType, String fieldName) {
        SemanticType fieldType = classInfo.fieldType(fieldName);
        if (fieldType == null || classInfo.typeParameters.isEmpty()) {
            return fieldType;
        }
        return typeSystem.applyTypeArguments(
                fieldType,
                typeSystem.classTypeBindings(classInfo, receiverType),
                java.util.Set.of()
        );
    }

    SemanticType signature(SemanticType returnType, SemanticType... parameterTypes) {
        return new SemanticType(Builtins.FUNCTION, false, functionType, returnType, List.of(parameterTypes));
    }

    boolean canAccessPrivateMember(ClassInfo owner) {
        return owner != null && currentClassInfo != null && owner.name.equals(currentClassInfo.name);
    }

    void resolveLocal(Expr expr, String name) {
        int distance = 0;
        int globalDistance = scopes.size() - 1;
        for (Map<String, Boolean> scope : scopes) {
            if (scope.containsKey(name)) {
                if (distance < globalDistance) {
                    interpreter.resolve(expr, distance);
                }
                return;
            }
            distance++;
        }
    }

    SemanticType functionValueType(FunctionLiteralExpr function) {
        resolveFunctionSignature(function);
        return new SemanticType(
                "Function",
                false,
                functionType,
                function.getResolvedReturnType(),
                function.parameters.stream().map(Parameter::getResolvedType).toList(),
                function.typeParameters.stream()
                        .map(typeParameter -> SemanticType.typeParameter(
                                typeParameter.name,
                                typeParameter.getResolvedBoundType() != null ? typeParameter.getResolvedBoundType() : objectType
                        ))
                        .toList()
        );
    }

    void ensureSymbolVisible(SymbolInfo symbol, String name) {
        if (symbol == null || importVisibility == null) {
            return;
        }
        String referenceFile = currentSourceFile();
        if (referenceFile == null) {
            return;
        }
        if (importVisibility.canReference(referenceFile, symbol.sourceFile())) {
            return;
        }
        throw BumpException.semantic("Symbol '" + name + "' is not visible here. Import it directly.");
    }

    private String currentSourceFile() {
        return BumpException.sourceFileForLine(activeLine);
    }

    SemanticType resolveFunctionOverload(String name, List<SemanticType> overloads, List<SemanticType> argumentTypes, String targetDescription) {
        if (overloads.isEmpty()) {
            return null;
        }

        SemanticType best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;
        for (SemanticType overload : overloads) {
            SemanticType instantiated = typeSystem.instantiateGenericOverload(overload, argumentTypes);
            if (instantiated == null) {
                continue;
            }
            int score = overloadMatchScore(instantiated, argumentTypes);
            if (score < 0) {
                continue;
            }
            if (score < bestScore) {
                best = instantiated;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }

        if (best == null) {
            throw BumpException.semantic("No overload of '" + targetDescription + "' matches the provided arguments.");
        }
        if (ambiguous) {
            throw BumpException.semantic("Call to overloaded function '" + targetDescription + "' is ambiguous.");
        }
        return best;
    }

    SemanticType resolveFunctionOverloadWithExplicitTypeArguments(
            String targetDescription,
            List<SemanticType> overloads,
            List<SemanticType> explicitTypeArguments,
            List<SemanticType> argumentTypes
    ) {
        if (overloads.isEmpty()) {
            return null;
        }

        List<SemanticType> candidates = new ArrayList<>();
        for (SemanticType overload : overloads) {
            SemanticType instantiated = typeSystem.instantiateOverloadWithExplicitTypeArguments(overload, explicitTypeArguments);
            if (instantiated == null) {
                continue;
            }
            if (overloadMatchScore(instantiated, argumentTypes) >= 0) {
                candidates.add(instantiated);
            }
        }

        if (candidates.isEmpty()) {
            throw BumpException.semantic("No overload of '" + targetDescription + "' matches the provided arguments.");
        }
        if (candidates.size() > 1) {
            throw BumpException.semantic("Call to overloaded function '" + targetDescription + "' is ambiguous.");
        }
        return candidates.get(0);
    }

    int overloadMatchScore(SemanticType overload, List<SemanticType> argumentTypes) {
        if (argumentTypes.size() != overload.parameterTypes().size()) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < argumentTypes.size(); i++) {
            int distance = typeSystem.typeDistance(argumentTypes.get(i), overload.parameterTypes().get(i));
            if (distance < 0) {
                return -1;
            }
            score += distance;
        }
        return score;
    }

    static boolean sameSignature(SemanticType left, SemanticType right) {
        if (left.parameterTypes().size() != right.parameterTypes().size()) {
            return false;
        }
        for (int i = 0; i < left.parameterTypes().size(); i++) {
            SemanticType leftParam = left.parameterTypes().get(i);
            SemanticType rightParam = right.parameterTypes().get(i);
            if (leftParam == null || rightParam == null) {
                if (leftParam != rightParam) {
                    return false;
                }
                continue;
            }
            if (leftParam.isTypeParameter() != rightParam.isTypeParameter()) {
                return false;
            }
            if (leftParam.isTypeParameter()) {
                SemanticType leftBound = leftParam.upperBound();
                SemanticType rightBound = rightParam.upperBound();
                String leftBoundName = leftBound != null ? leftBound.name() : null;
                String rightBoundName = rightBound != null ? rightBound.name() : null;
                if (!java.util.Objects.equals(leftBoundName, rightBoundName)) {
                    return false;
                }
                continue;
            }
            if (!leftParam.name().equals(rightParam.name())) {
                return false;
            }
            if (!leftParam.displayName().equals(rightParam.displayName())) {
                return false;
            }
        }
        return true;
    }

    static boolean satisfiesAbstractSignature(SemanticType required, SemanticType implementation) {
        if (required.parameterTypes().size() != implementation.parameterTypes().size()) {
            return false;
        }
        SemanticType requiredReturn = required.returnType();
        SemanticType implementationReturn = implementation.returnType();
        if (requiredReturn != null && implementationReturn != null && !requiredReturn.isAssignableFrom(implementationReturn)) {
            return false;
        }
        for (int i = 0; i < required.parameterTypes().size(); i++) {
            SemanticType requiredParam = required.parameterTypes().get(i);
            SemanticType implementationParam = implementation.parameterTypes().get(i);
            if (requiredParam == null || implementationParam == null) {
                if (requiredParam != implementationParam) {
                    return false;
                }
                continue;
            }
            if (!requiredParam.isAssignableFrom(implementationParam)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Void visitIntegerLiteral(IntegerLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitFloatLiteral(FloatLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitStringLiteral(StringLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitBooleanLiteral(BooleanLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitArrayLiteral(ArrayLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitMapLiteral(MapLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitNullLiteral(NullLiteral expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitArrayIndex(ArrayIndex expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitSetIndex(SetIndex expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitCompoundSetExpr(CompoundSetExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitVariableExpr(VariableExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitAdd(Add expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitSub(Sub expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitMultiply(Multiply expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitDivide(Divide expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitModulo(Modulo expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitNegative(Negative expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitEquality(Equality expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitInequality(Inequality expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitGreater(Greater expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitGreaterEqual(GreaterEqual expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitLess(Less expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitLessEqual(LessEqual expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitAnd(And expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitOr(Or expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitNot(Not expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitFunctionLiteralExpr(FunctionLiteralExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitCallExpr(CallExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitGetExpr(GetExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitSetExpr(SetExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public Void visitSuperExpr(SuperExpr expr) {
        expressionAnalyzer.analyze(expr);
        return null;
    }

    @Override
    public void visitBlockStmt(BlockStmt stmt) {
        statementAnalyzer.visitBlockStmt(stmt);
    }

    @Override
    public void visitWhileStmt(WhileStmt stmt) {
        statementAnalyzer.visitWhileStmt(stmt);
    }

    @Override
    public void visitForStmt(ForStmt stmt) {
        statementAnalyzer.visitForStmt(stmt);
    }

    @Override
    public void visitIfStmt(IfStmt stmt) {
        statementAnalyzer.visitIfStmt(stmt);
    }

    @Override
    public void visitVariableDeclarationStmt(VariableDeclarationStmt stmt) {
        statementAnalyzer.visitVariableDeclarationStmt(stmt);
    }

    @Override
    public void visitReturnStmt(ReturnStmt stmt) {
        statementAnalyzer.visitReturnStmt(stmt);
    }

    @Override
    public void visitBreakStmt(BreakStmt stmt) {
        statementAnalyzer.visitBreakStmt(stmt);
    }

    @Override
    public void visitContinueStmt(ContinueStmt stmt) {
        statementAnalyzer.visitContinueStmt(stmt);
    }

    @Override
    public void visitSwitchStmt(SwitchStmt stmt) {
        statementAnalyzer.visitSwitchStmt(stmt);
    }

    @Override
    public void visitTryStmt(TryStmt stmt) {
        statementAnalyzer.visitTryStmt(stmt);
    }

    @Override
    public void visitClassStmt(ClassStmt stmt) {
        statementAnalyzer.visitClassStmt(stmt);
    }

    @Override
    public void visitEnumStmt(EnumStmt stmt) {
        statementAnalyzer.visitEnumStmt(stmt);
    }

    @Override
    public void visitExtendStmt(ExtendStmt stmt) {
        statementAnalyzer.visitExtendStmt(stmt);
    }

    @Override
    public void visitExpressionStmt(ExpressionStmt stmt) {
        statementAnalyzer.visitExpressionStmt(stmt);
    }

    @Override
    public void visitJavaBlockStmt(JavaBlockStmt stmt) {
        statementAnalyzer.visitJavaBlockStmt(stmt);
    }
}
