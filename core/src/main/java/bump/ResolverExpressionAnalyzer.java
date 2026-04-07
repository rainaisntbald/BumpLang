package bump;

import ast.*;

import java.util.ArrayList;
import java.util.List;

final class ResolverExpressionAnalyzer {
    private final Resolver resolver;

    ResolverExpressionAnalyzer(Resolver resolver) {
        this.resolver = resolver;
    }

    SemanticType analyze(Expr expr) {
        try {
            return analyzeInternal(expr);
        } catch (BumpException error) {
            throw error.withFallbackLocation(expr.getLine(), expr.getColumn(), expr.getLength(), expr.describeLocation());
        }
    }

    private SemanticType analyzeInternal(Expr expr) {
        if (expr instanceof IntegerLiteral) return resolver.integerType;
        if (expr instanceof FloatLiteral) return resolver.floatType;
        if (expr instanceof StringLiteral) return resolver.stringType;
        if (expr instanceof BooleanLiteral) return resolver.boolType;
        if (expr instanceof NullLiteral) return resolver.nullType;
        if (expr instanceof ArrayLiteral arrayLiteral) {
            for (Expr element : arrayLiteral.elements) {
                analyze(element);
            }
            return resolver.arrayType;
        }
        if (expr instanceof MapLiteral mapLiteral) {
            for (Expr key : mapLiteral.keys) {
                analyze(key);
            }
            for (Expr value : mapLiteral.values) {
                analyze(value);
            }
            return resolver.mapType;
        }
        if (expr instanceof FunctionLiteralExpr functionLiteralExpr) {
            resolver.resolveFunction(functionLiteralExpr, FunctionType.FUNCTION);
            return resolver.functionValueType(functionLiteralExpr);
        }
        if (expr instanceof VariableExpr variableExpr) {
            return analyzeVariableExpr(variableExpr);
        }
        if (expr instanceof Add add) {
            return analyzeBinaryMethod(add.left, add.right, "_add");
        }
        if (expr instanceof Sub sub) {
            return analyzeBinaryMethod(sub.left, sub.right, "_sub");
        }
        if (expr instanceof Multiply multiply) {
            return analyzeBinaryMethod(multiply.left, multiply.right, "_mul");
        }
        if (expr instanceof Divide divide) {
            return analyzeBinaryMethod(divide.left, divide.right, "_div");
        }
        if (expr instanceof Modulo modulo) {
            return analyzeBinaryMethod(modulo.left, modulo.right, "_mod");
        }
        if (expr instanceof Negative negative) {
            return analyzeUnaryMethod(negative.expr, "_neg");
        }
        if (expr instanceof Equality equality) {
            analyzeEqualityOperands(equality.left, equality.right);
            return resolver.boolType;
        }
        if (expr instanceof Inequality inequality) {
            analyzeEqualityOperands(inequality.left, inequality.right);
            return resolver.boolType;
        }
        if (expr instanceof Greater greater) {
            return analyzeBinaryMethod(greater.left, greater.right, "_gt");
        }
        if (expr instanceof GreaterEqual greaterEqual) {
            return analyzeBinaryMethod(greaterEqual.left, greaterEqual.right, "_gte");
        }
        if (expr instanceof Less less) {
            return analyzeBinaryMethod(less.left, less.right, "_lt");
        }
        if (expr instanceof LessEqual lessEqual) {
            return analyzeBinaryMethod(lessEqual.left, lessEqual.right, "_lte");
        }
        if (expr instanceof And and) {
            analyzeRequiredType(and.left, resolver.boolType, "Expected a Bool value, but got %s.");
            analyzeRequiredType(and.right, resolver.boolType, "Expected a Bool value, but got %s.");
            return resolver.boolType;
        }
        if (expr instanceof Or or) {
            analyzeRequiredType(or.left, resolver.boolType, "Expected a Bool value, but got %s.");
            analyzeRequiredType(or.right, resolver.boolType, "Expected a Bool value, but got %s.");
            return resolver.boolType;
        }
        if (expr instanceof Not not) {
            analyzeRequiredType(not.expr, resolver.boolType, "Expected a Bool value, but got %s.");
            return resolver.boolType;
        }
        if (expr instanceof CallExpr callExpr) {
            return analyzeCallExpr(callExpr);
        }
        if (expr instanceof GetExpr getExpr) {
            return analyzeGetExpr(getExpr);
        }
        if (expr instanceof SetExpr setExpr) {
            return analyzeSetExpr(setExpr);
        }
        if (expr instanceof SuperExpr superExpr) {
            return analyzeSuperExpr(superExpr);
        }
        if (expr instanceof ArrayIndex arrayIndex) {
            return analyzeArrayIndex(arrayIndex);
        }
        if (expr instanceof SetIndex setIndex) {
            return analyzeSetIndex(setIndex);
        }
        if (expr instanceof CompoundSetExpr compoundSetExpr) {
            return analyzeCompoundSetExpr(compoundSetExpr);
        }
        throw BumpException.internal("Unsupported expression node: " + expr.getClass().getSimpleName());
    }

    SemanticType analyzeRequiredType(Expr expr, SemanticType expectedType, String messageTemplate) {
        SemanticType actualType = analyze(expr);
        if (actualType == null) {
            return null;
        }
        if (actualType == resolver.nullType) {
            throw BumpException.semantic(messageTemplate.formatted(actualType.name()));
        }
        if (expectedType != null && !expectedType.isAssignableFrom(actualType)) {
            throw BumpException.semantic(messageTemplate.formatted(actualType.name()));
        }
        return actualType;
    }

    private SemanticType analyzeVariableExpr(VariableExpr expr) {
        if (expr.name.equals("this") && resolver.currentClass == ClassType.NONE) {
            throw BumpException.semantic("Cannot use 'this' here.");
        }
        if (!resolver.scopes.isEmpty()) {
            Boolean defined = resolver.scopes.peek().get(expr.name);
            if (Boolean.FALSE.equals(defined)) {
                throw BumpException.semantic("Cannot read local variable '" + expr.name + "' in its own initializer.");
            }
        }
        SymbolInfo symbol = resolver.lookupSymbol(expr.name);
        String resolvedName = expr.name;
        if (symbol == null) {
            String baseName = extractBaseName(expr.name);
            if (!baseName.equals(expr.name)) {
                symbol = resolver.lookupSymbol(baseName);
                resolvedName = baseName;
            }
        }
        if (symbol == null) {
            if (expr.name.indexOf('<') >= 0) {
                return null;
            }
            throw BumpException.semantic("Undefined variable '" + expr.name + "'.");
        }
        resolver.ensureSymbolVisible(symbol, resolvedName);
        resolver.resolveLocal(expr, resolvedName);
        if (symbol.kind() == SymbolKind.FUNCTION && symbol.overloads().size() > 1) {
            return new SemanticType(Builtins.FUNCTION, false, resolver.functionType, null, List.of(), false);
        }
        return symbol.semanticType();
    }

    private SemanticType analyzeCallExpr(CallExpr expr) {
        SemanticType calleeType = analyze(expr.callee);
        List<SemanticType> argumentTypes = expr.arguments.stream().map(this::analyze).toList();
        List<SemanticType> explicitTypeArguments = resolveExplicitTypeArguments(expr);

        if (expr.callee instanceof VariableExpr variableExpr) {
            String baseName = extractBaseName(variableExpr.name);
            List<String> constructorTypeArgumentNames = expr.typeArguments.isEmpty()
                    ? extractTypeArguments(variableExpr.name)
                    : expr.typeArguments;

            SymbolInfo symbol = resolver.lookupSymbol(baseName);
            if (symbol != null && symbol.kind() == SymbolKind.CLASS) {
                resolver.ensureSymbolVisible(symbol, baseName);
                ClassInfo classInfo = symbol.classInfo();
                validateTypeArguments(classInfo, constructorTypeArgumentNames, variableExpr.name);
                List<SemanticType> constructorOverloads = constructorTypes(symbol);
                validateConstructorCall(symbol, argumentTypes, constructorOverloads);

                if (!constructorTypeArgumentNames.isEmpty()) {
                    List<SemanticType> boundTypes = constructorTypeArgumentNames.stream()
                        .map(typeArg -> resolver.resolveTypeName(typeArg, "Unknown type"))
                        .toList();
                    SemanticType baseType = symbol.semanticType();
                    return new SemanticType(
                        baseType.name(),
                        baseType.isNativeClass(),
                        baseType.isExtendable(),
                        baseType.superclass(),
                        baseType.interfaces(),
                        boundTypes
                    );
                }
                return symbol.semanticType();
            }
            if (symbol != null && symbol.kind() == SymbolKind.FUNCTION) {
                resolver.ensureSymbolVisible(symbol, variableExpr.name);
                if (!explicitTypeArguments.isEmpty()) {
                    SemanticType overload = resolver.resolveFunctionOverloadWithExplicitTypeArguments(
                            variableExpr.name,
                            symbol.overloads(),
                            explicitTypeArguments,
                            argumentTypes
                    );
                    return overload == null ? null : overload.returnType();
                }
                if (symbol.overloads().size() == 1 && symbol.overloads().get(0).typeParameters().isEmpty()) {
                    SemanticType overload = symbol.overloads().get(0);
                    validateArgumentTypes(overload.parameterTypes(), argumentTypes, "call");
                    return overload.returnType();
                }
                SemanticType overload = resolver.resolveFunctionOverload(variableExpr.name, symbol.overloads(), argumentTypes, variableExpr.name);
                return overload == null ? null : overload.returnType();
            }
        }

        if (expr.callee instanceof GetExpr getExpr) {
            if (getExpr.object instanceof VariableExpr variableExpr) {
                SymbolInfo symbol = resolver.lookupSymbol(variableExpr.name);
                if (symbol == null) {
                    symbol = resolver.lookupSymbol(extractBaseName(variableExpr.name));
                }
                if (symbol != null && symbol.kind() == SymbolKind.CLASS && symbol.classInfo() != null && !symbol.classInfo().enumValues.isEmpty()) {
                    EnumVariantInfo enumVariant = symbol.classInfo().enumValues.get(getExpr.name);
                    if (enumVariant == null) {
                        throw BumpException.semantic("Enum '" + symbol.classInfo().name + "' has no variant '" + getExpr.name + "'.");
                    }
                    SemanticType receiverEnumType = symbol.semanticType();
                    List<String> explicitReceiverArgs = extractTypeArguments(variableExpr.name);
                    if (!explicitReceiverArgs.isEmpty()) {
                        if (symbol.classInfo().typeParameters.size() != explicitReceiverArgs.size()) {
                            throw BumpException.semantic("Type '" + symbol.classInfo().name + "' expects " + symbol.classInfo().typeParameters.size()
                                    + " type argument(s), but got " + explicitReceiverArgs.size() + ".");
                        }
                        List<SemanticType> resolvedReceiverArgs = explicitReceiverArgs.stream()
                                .map(typeName -> resolver.resolveTypeName(typeName, "Unknown type"))
                                .toList();
                        receiverEnumType = receiverEnumType.withTypeArguments(resolvedReceiverArgs);
                    }
                    int expectedArity = enumVariant.payloadName == null ? 0 : 1;
                    if (argumentTypes.size() != expectedArity) {
                        throw BumpException.semantic("Expected " + expectedArity + " arguments for enum variant '" + getExpr.name + "' but got " + argumentTypes.size() + ".");
                    }
                    if (enumVariant.payloadName != null && !argumentTypes.isEmpty()) {
                        SemanticType arg = argumentTypes.get(0);
                        SemanticType expected = resolver.bindFieldType(symbol.classInfo(), receiverEnumType, enumVariant.payloadName);
                        if (expected != null && arg != null && arg != resolver.nullType && !expected.isAssignableFrom(arg)) {
                            throw BumpException.semantic("Cannot pass " + arg.displayName() + " to argument 1 of type " + expected.displayName() + ".");
                        }
                    }
                    return receiverEnumType;
                }
            }
            SemanticType objectType = analyze(getExpr.object);
            ClassInfo classInfo = requireClassInfo(objectType, "Undefined property '" + getExpr.name + "'.");
            ClassInfo methodOwner = classInfo.methodOwner(getExpr.name);
            if (methodOwner != null && classInfo.isMethodPrivate(getExpr.name) && !resolver.canAccessPrivateMember(methodOwner)) {
                throw BumpException.semantic("Method '" + getExpr.name + "' is private in class '" + methodOwner.name + "'.");
            }
            List<SemanticType> overloads = resolver.bindMethodOverloads(classInfo, objectType, getExpr.name);
            if (!explicitTypeArguments.isEmpty()) {
                SemanticType overload = resolver.resolveFunctionOverloadWithExplicitTypeArguments(
                        getExpr.name,
                        overloads,
                        explicitTypeArguments,
                        argumentTypes
                );
                return overload == null ? null : overload.returnType();
            }
            if (overloads.size() == 1 && overloads.get(0).typeParameters().isEmpty()) {
                SemanticType overload = overloads.get(0);
                validateArgumentTypes(overload.parameterTypes(), argumentTypes, "call");
                return overload.returnType();
            }
            SemanticType overload = resolver.resolveFunctionOverload(getExpr.name, overloads, argumentTypes, getExpr.name);
            return overload == null ? null : overload.returnType();
        }

        if (expr.callee instanceof SuperExpr superExpr) {
            if (resolver.currentClassInfo == null || resolver.currentClassInfo.superclass == null) {
                throw BumpException.semantic("Cannot use 'super' here.");
            }
            List<SemanticType> overloads = resolver.currentClassInfo.superclass.methodOverloads(superExpr.member.getText());
            if (!explicitTypeArguments.isEmpty()) {
                SemanticType overload = resolver.resolveFunctionOverloadWithExplicitTypeArguments(
                        superExpr.member.getText(),
                        overloads,
                        explicitTypeArguments,
                        argumentTypes
                );
                return overload == null ? null : overload.returnType();
            }
            if (overloads.size() == 1 && overloads.get(0).typeParameters().isEmpty()) {
                SemanticType overload = overloads.get(0);
                validateArgumentTypes(overload.parameterTypes(), argumentTypes, "call");
                return overload.returnType();
            }
            SemanticType overload = resolver.resolveFunctionOverload(
                    superExpr.member.getText(),
                    overloads,
                    argumentTypes,
                    superExpr.member.getText()
            );
            return overload == null ? null : overload.returnType();
        }

        if (calleeType == null) {
            return null;
        }
        if (!Builtins.FUNCTION.equals(calleeType.name())) {
            throw BumpException.semantic("Cannot call value of type " + describeType(calleeType) + ".");
        }
        if (!calleeType.signatureKnown()) {
            return null;
        }
        if (!explicitTypeArguments.isEmpty()) {
            SemanticType overload = resolver.resolveFunctionOverloadWithExplicitTypeArguments(
                    "call",
                    List.of(calleeType),
                    explicitTypeArguments,
                    argumentTypes
            );
            return overload == null ? null : overload.returnType();
        }
        if (!calleeType.typeParameters().isEmpty()) {
            SemanticType overload = resolver.resolveFunctionOverload("call", List.of(calleeType), argumentTypes, "call");
            return overload == null ? null : overload.returnType();
        }
        validateArgumentTypes(calleeType.parameterTypes(), argumentTypes, "call");
        return calleeType.returnType();
    }

    private List<SemanticType> resolveExplicitTypeArguments(CallExpr expr) {
        if (expr.typeArguments.isEmpty()) {
            return List.of();
        }
        return expr.typeArguments.stream()
                .map(typeName -> resolver.resolveTypeName(typeName, "Unknown type"))
                .toList();
    }

    private SemanticType analyzeGetExpr(GetExpr expr) {
        if (expr.object instanceof VariableExpr variableExpr) {
            SymbolInfo symbol = resolver.lookupSymbol(variableExpr.name);
            if (symbol == null) {
                symbol = resolver.lookupSymbol(extractBaseName(variableExpr.name));
            }
            if (symbol != null && symbol.kind() == SymbolKind.CLASS && symbol.classInfo() != null && !symbol.classInfo().enumValues.isEmpty()) {
                resolver.ensureSymbolVisible(symbol, variableExpr.name);
                EnumVariantInfo enumValue = symbol.classInfo().enumValues.get(expr.name);
                if (enumValue == null) {
                    throw BumpException.semantic("Enum '" + symbol.classInfo().name + "' has no variant '" + expr.name + "'.");
                }
                SemanticType receiverEnumType = symbol.semanticType();
                List<String> explicitReceiverArgs = extractTypeArguments(variableExpr.name);
                if (!explicitReceiverArgs.isEmpty()) {
                    if (symbol.classInfo().typeParameters.size() != explicitReceiverArgs.size()) {
                        throw BumpException.semantic("Type '" + symbol.classInfo().name + "' expects " + symbol.classInfo().typeParameters.size()
                                + " type argument(s), but got " + explicitReceiverArgs.size() + ".");
                    }
                    List<SemanticType> resolvedReceiverArgs = explicitReceiverArgs.stream()
                            .map(typeName -> resolver.resolveTypeName(typeName, "Unknown type"))
                            .toList();
                    receiverEnumType = receiverEnumType.withTypeArguments(resolvedReceiverArgs);
                }
                if (enumValue.payloadName != null) {
                    SemanticType payloadType = resolver.bindFieldType(symbol.classInfo(), receiverEnumType, enumValue.payloadName);
                    if (payloadType == null) {
                        payloadType = enumValue.payloadType == null ? resolver.objectType : enumValue.payloadType;
                    }
                    return resolver.signature(receiverEnumType, payloadType);
                }
                return receiverEnumType;
            }
        }
        SemanticType objectExprType = analyze(expr.object);
        if (objectExprType == null) {
            return null;
        }
        ClassInfo classInfo = requireClassInfo(objectExprType, "Undefined property '" + expr.name + "'.");
        ClassInfo fieldOwner = classInfo.fieldOwner(expr.name);
        if (fieldOwner != null && classInfo.isFieldPrivate(expr.name) && !resolver.canAccessPrivateMember(fieldOwner)) {
            throw BumpException.semantic("Field '" + expr.name + "' is private in class '" + fieldOwner.name + "'.");
        }
        SemanticType fieldType = resolver.bindFieldType(classInfo, objectExprType, expr.name);
        if (fieldType != null) {
            return fieldType;
        }
        ClassInfo methodOwner = classInfo.methodOwner(expr.name);
        if (methodOwner != null && classInfo.isMethodPrivate(expr.name) && !resolver.canAccessPrivateMember(methodOwner)) {
            throw BumpException.semantic("Method '" + expr.name + "' is private in class '" + methodOwner.name + "'.");
        }
        List<SemanticType> methodOverloads = resolver.bindMethodOverloads(classInfo, objectExprType, expr.name);
        SemanticType methodType = methodOverloads.size() == 1
                ? methodOverloads.get(0)
                : (methodOverloads.isEmpty() ? null : new SemanticType(Builtins.FUNCTION, false, resolver.functionType, null, List.of(), false));
        if (methodType != null) {
            return methodType;
        }
        throw BumpException.semantic("Undefined property '" + expr.name + "'.");
    }

    private SemanticType analyzeSetExpr(SetExpr expr) {
        SemanticType valueType = analyze(expr.value);
        if (expr.object == null) {
            resolver.resolveAssignment(expr, expr.name);
            SymbolInfo symbol = resolver.lookupSymbol(expr.name);
            if (symbol == null) {
                throw BumpException.semantic("Undefined variable '" + expr.name + "'.");
            }
            if (symbol.kind() == SymbolKind.VARIABLE) {
                validateAssignmentCompatibility(symbol.semanticType(), valueType, "variable '" + expr.name + "'");
                return symbol.semanticType();
            }
            throw BumpException.semantic("Cannot assign to built-in name '" + expr.name + "'.");
        }

        SemanticType objectType = analyze(expr.object);
        if (objectType == null) {
            return valueType;
        }
        ClassInfo classInfo = requireClassInfo(objectType, "Field '" + expr.name + "' is not declared on class '" + describeType(objectType) + "'.");
        if (classInfo.hasMethod(expr.name)) {
            throw BumpException.semantic("Cannot assign to field '" + expr.name + "' because it conflicts with an existing method.");
        }
        ClassInfo fieldOwner = classInfo.fieldOwner(expr.name);
        if (fieldOwner != null && classInfo.isFieldPrivate(expr.name) && !resolver.canAccessPrivateMember(fieldOwner)) {
            throw BumpException.semantic("Field '" + expr.name + "' is private in class '" + fieldOwner.name + "'.");
        }
        SemanticType fieldType = resolver.bindFieldType(classInfo, objectType, expr.name);
        if (fieldType == null) {
            throw BumpException.semantic("Field '" + expr.name + "' is not declared on class '" + classInfo.name + "'.");
        }
        validateFieldAssignmentCompatibility(fieldType, valueType, expr.name, classInfo.name);
        return fieldType;
    }

    private SemanticType analyzeSuperExpr(SuperExpr expr) {
        if (resolver.currentClass != ClassType.SUBCLASS || resolver.currentClassInfo == null || resolver.currentClassInfo.superclass == null) {
            throw BumpException.semantic("Cannot use 'super' here.");
        }
        resolver.resolveLocal(expr, "super");
        List<SemanticType> overloads = resolver.bindMethodOverloads(
                resolver.currentClassInfo.superclass,
                resolver.currentClassInfo.semanticType,
                expr.member.getText()
        );
        ClassInfo methodOwner = resolver.currentClassInfo.superclass.methodOwner(expr.member.getText());
        if (methodOwner != null
                && resolver.currentClassInfo.superclass.isMethodPrivate(expr.member.getText())
                && !resolver.canAccessPrivateMember(methodOwner)) {
            throw BumpException.semantic("Method '" + expr.member.getText() + "' is private in class '" + methodOwner.name + "'.");
        }
        SemanticType methodType = overloads.size() == 1
                ? overloads.get(0)
                : (overloads.isEmpty() ? null : new SemanticType(Builtins.FUNCTION, false, resolver.functionType, null, List.of(), false));
        if (methodType == null) {
            throw BumpException.semantic("Undefined superclass method '" + expr.member.getText() + "'.");
        }
        return methodType;
    }

    private SemanticType analyzeArrayIndex(ArrayIndex expr) {
        SemanticType targetType = analyze(expr.array);
        analyzeRequiredType(expr.index, resolver.integerType, "Array and string indices must be Integer values, but got %s.");
        if (targetType == null) {
            return null;
        }
        if (targetType == resolver.arrayType) {
            return null;
        }
        if (targetType == resolver.stringType) {
            return resolver.charType;
        }
        throw BumpException.semantic("Cannot index value of type " + describeType(targetType) + ".");
    }

    private SemanticType analyzeSetIndex(SetIndex expr) {
        SemanticType targetType = analyze(expr.array);
        analyzeRequiredType(expr.index, resolver.integerType, "Array and string indices must be Integer values, but got %s.");
        SemanticType valueType = analyze(expr.value);
        if (targetType == null) {
            return valueType;
        }
        if (targetType != resolver.arrayType) {
            throw BumpException.semantic("Cannot assign through an index on value of type " + describeType(targetType) + ".");
        }
        return valueType;
    }

    private SemanticType analyzeCompoundSetExpr(CompoundSetExpr expr) {
        SemanticType valueType = analyze(expr.value);
        if (expr.target instanceof VariableExpr variableExpr) {
            SemanticType targetType = analyzeVariableExpr(variableExpr);
            resolver.resolveAssignment(expr, variableExpr.name);
            SemanticType resultType = analyzeOperatorOnTypes(targetType, valueType, compoundOperatorMethod(expr.operator));
            SymbolInfo symbol = resolver.lookupSymbol(variableExpr.name);
            if (symbol != null && symbol.kind() == SymbolKind.VARIABLE) {
                validateAssignmentCompatibility(symbol.semanticType(), resultType, "variable '" + variableExpr.name + "'");
            }
            return resultType;
        }
        if (expr.target instanceof GetExpr getExpr) {
            SemanticType objectType = analyze(getExpr.object);
            if (objectType == null) {
                return null;
            }
            ClassInfo classInfo = requireClassInfo(objectType, "Undefined property '" + getExpr.name + "'.");
            if (classInfo.hasMethod(getExpr.name)) {
                throw BumpException.semantic("Cannot assign to field '" + getExpr.name + "' because it conflicts with an existing method.");
            }
            ClassInfo fieldOwner = classInfo.fieldOwner(getExpr.name);
            if (fieldOwner != null && classInfo.isFieldPrivate(getExpr.name) && !resolver.canAccessPrivateMember(fieldOwner)) {
                throw BumpException.semantic("Field '" + getExpr.name + "' is private in class '" + fieldOwner.name + "'.");
            }
            SemanticType fieldType = classInfo.fieldType(getExpr.name);
            if (fieldType == null) {
                throw BumpException.semantic("Undefined property '" + getExpr.name + "'.");
            }
            SemanticType resultType = analyzeOperatorOnTypes(fieldType, valueType, compoundOperatorMethod(expr.operator));
            validateAssignmentCompatibility(fieldType, resultType, "field '" + getExpr.name + "' on class '" + classInfo.name + "'");
            return resultType;
        }
        if (expr.target instanceof ArrayIndex arrayIndex) {
            SemanticType targetType = analyze(arrayIndex.array);
            analyzeRequiredType(arrayIndex.index, resolver.integerType, "Array and string indices must be Integer values, but got %s.");
            if (targetType == null) {
                return null;
            }
            if (targetType != resolver.arrayType) {
                throw BumpException.semantic("Cannot assign through an index on value of type " + describeType(targetType) + ".");
            }
            return null;
        }
        throw BumpException.semantic("Invalid assignment target.");
    }

    private SemanticType analyzeBinaryMethod(Expr left, Expr right, String methodName) {
        return analyzeOperatorOnTypes(analyze(left), analyze(right), methodName);
    }

    private SemanticType analyzeUnaryMethod(Expr operand, String methodName) {
        SemanticType operandType = analyze(operand);
        ClassInfo classInfo = requireClassInfo(operandType, "Cannot apply operator to value of type " + describeType(operandType) + ".");
        List<SemanticType> overloads = classInfo.methodOverloads(methodName);
        SemanticType methodType = overloads.size() == 1
                ? overloads.get(0)
                : resolver.resolveFunctionOverload(methodName, overloads, List.of(), methodName);
        if (methodType == null) {
            throw BumpException.semantic("Cannot apply operator to value of type " + classInfo.name + ".");
        }
        return methodType.returnType();
    }

    private void analyzeEqualityOperands(Expr left, Expr right) {
        SemanticType leftType = analyze(left);
        SemanticType rightType = analyze(right);
        if (leftType == resolver.nullType || rightType == resolver.nullType) {
            return;
        }
        analyzeOperatorOnTypes(leftType, rightType, "_eq");
    }

    private SemanticType analyzeOperatorOnTypes(SemanticType receiverType, SemanticType argumentType, String methodName) {
        if (receiverType == null) {
            return null;
        }
        ClassInfo classInfo = requireClassInfo(receiverType, "Cannot apply operator to value of type " + describeType(receiverType) + ".");
        List<SemanticType> overloads = resolver.bindMethodOverloads(classInfo, receiverType, methodName);
        List<SemanticType> argumentTypes = singletonListAllowingNull(argumentType);
        SemanticType methodType;
        if (overloads.size() == 1) {
            methodType = overloads.get(0);
        } else {
            try {
                methodType = resolver.resolveFunctionOverload(methodName, overloads, argumentTypes, methodName);
            } catch (BumpException error) {
                List<SemanticType> sameArity = overloads.stream()
                        .filter(overload -> overload.parameterTypes().size() == 1)
                        .toList();
                if (!sameArity.isEmpty() && error.getDetail().startsWith("No overload of")) {
                    validateArgumentTypes(sameArity.get(0).parameterTypes(), argumentTypes, "operation on " + classInfo.name);
                }
                throw error;
            }
        }
        if (methodType == null) {
            throw BumpException.semantic("Cannot apply operator to value of type " + classInfo.name + ".");
        }
        if ("_add".equals(methodName)
                && receiverType == resolver.stringType
                && argumentType != null
                && argumentType != resolver.nullType
                && !resolver.stringType.isAssignableFrom(argumentType)
                && !resolver.charType.isAssignableFrom(argumentType)) {
            throw BumpException.semantic("Cannot concatenate String with " + argumentType.name() + ".");
        }
        if ("_add".equals(methodName)
                && receiverType == resolver.charType
                && argumentType != null
                && argumentType != resolver.nullType
                && !resolver.stringType.isAssignableFrom(argumentType)
                && !resolver.charType.isAssignableFrom(argumentType)) {
            throw BumpException.semantic("Cannot concatenate Char with " + argumentType.name() + ".");
        }
        if (argumentType != null) {
            validateArgumentTypes(methodType.parameterTypes(), argumentTypes, "operation on " + classInfo.name);
        }
        return methodType.returnType();
    }

    private List<SemanticType> singletonListAllowingNull(SemanticType type) {
        List<SemanticType> values = new ArrayList<>(1);
        values.add(type);
        return values;
    }

    void validateAssignmentCompatibility(SemanticType targetType, SemanticType valueType, String targetDescription) {
        if (targetType == null || valueType == null || valueType == resolver.nullType) {
            return;
        }
        if (!targetType.isAssignableFrom(valueType)) {
            throw BumpException.semantic("Cannot assign " + valueType.displayName() + " to " + targetDescription + " of type " + targetType.displayName() + ".");
        }
    }

    void validateArgumentTypes(List<SemanticType> parameterTypes, List<SemanticType> argumentTypes, String targetDescription) {
        if (argumentTypes.size() != parameterTypes.size()) {
            throw BumpException.semantic("Expected " + parameterTypes.size() + " arguments for " + targetDescription + " but got " + argumentTypes.size() + ".");
        }
        for (int i = 0; i < argumentTypes.size(); i++) {
            SemanticType parameterType = parameterTypes.get(i);
            SemanticType argumentType = argumentTypes.get(i);
            if (parameterType == null || argumentType == null || argumentType == resolver.nullType) {
                continue;
            }
            if (!parameterType.isAssignableFrom(argumentType)) {
                throw BumpException.semantic("Cannot pass " + argumentType.displayName() + " to argument " + (i + 1) + " of type " + parameterType.displayName() + ".");
            }
        }
    }

    void validateFieldAssignmentCompatibility(SemanticType fieldType, SemanticType valueType, String fieldName, String className) {
        if (fieldType == null || valueType == null || valueType == resolver.nullType) {
            return;
        }
        if (!fieldType.isAssignableFrom(valueType)) {
            throw BumpException.semantic("Field '" + fieldName + "' on class '" + className + "' must be of type " + fieldType.displayName() + ", but got " + valueType.displayName() + ".");
        }
    }

    private ClassInfo requireClassInfo(SemanticType semanticType, String failureMessage) {
        ClassInfo classInfo = resolver.classInfoForType(semanticType);
        if (classInfo == null) {
            throw BumpException.semantic(failureMessage);
        }
        return classInfo;
    }

    private List<SemanticType> constructorTypes(SymbolInfo symbol) {
        if (symbol == null || symbol.kind() != SymbolKind.CLASS || symbol.classInfo() == null) {
            return List.of();
        }
        SemanticType constructorReceiverType = symbol.semanticType();
        if (constructorReceiverType != null && constructorReceiverType.typeArguments().isEmpty() && !symbol.classInfo().typeParameters.isEmpty()) {
            constructorReceiverType = constructorReceiverType.withTypeArguments(symbol.classInfo().typeParameters);
        }
        List<SemanticType> initializerTypes = resolver.bindMethodOverloads(symbol.classInfo(), constructorReceiverType, "init");
        if (!initializerTypes.isEmpty()) {
            return initializerTypes;
        }
        if (!symbol.classInfo().nativeClass) {
            return List.of(resolver.signature(resolver.nullType));
        }
        return switch (symbol.classInfo().name) {
            case Builtins.ARRAY -> List.of(resolver.signature(resolver.arrayType, resolver.arrayType));
            case Builtins.BOOL -> List.of(resolver.signature(resolver.boolType, resolver.boolType));
            case Builtins.CHAR -> List.of(resolver.signature(resolver.charType, resolver.objectType));
            case Builtins.STRING -> List.of(resolver.signature(resolver.stringType, resolver.objectType));
            case Builtins.INTEGER -> List.of(resolver.signature(resolver.integerType, resolver.integerType));
            case Builtins.FLOAT -> List.of(resolver.signature(resolver.floatType, resolver.objectType));
            case Builtins.DATETIME -> List.of(
                    resolver.signature(resolver.dateTimeType, resolver.stringType),
                    resolver.signature(resolver.dateTimeType, resolver.dateTimeType),
                    resolver.signature(resolver.dateTimeType, resolver.integerType, resolver.integerType, resolver.integerType),
                    resolver.signature(resolver.dateTimeType, resolver.integerType, resolver.integerType, resolver.integerType, resolver.integerType, resolver.integerType, resolver.integerType)
            );
            case Builtins.MAP -> List.of(resolver.signature(resolver.mapType));
            case Builtins.FILE -> List.of(resolver.signature(resolver.fileType, resolver.stringType));
            default -> List.of(resolver.signature(resolver.nullType));
        };
    }

    private void validateConstructorCall(SymbolInfo symbol, List<SemanticType> argumentTypes, List<SemanticType> constructorTypes) {
        if (symbol == null || symbol.classInfo() == null) {
            return;
        }
        String className = symbol.classInfo().name;
        if (!symbol.classInfo().enumValues.isEmpty()) {
            throw BumpException.semantic("Cannot instantiate enum '" + className + "'.");
        }
        if (symbol.classInfo().abstractClass) {
            throw BumpException.semantic("Cannot instantiate abstract class '" + className + "'.");
        }
        ClassInfo initOwner = symbol.classInfo().methodOwner("init");
        if (initOwner != null && symbol.classInfo().isMethodPrivate("init") && !resolver.canAccessPrivateMember(initOwner)) {
            throw BumpException.semantic("Method 'init' is private in class '" + initOwner.name + "'.");
        }
        if (constructorTypes.size() == 1) {
            List<SemanticType> parameterTypes = constructorTypes.get(0).parameterTypes();
            if (argumentTypes.size() != parameterTypes.size()) {
                throw BumpException.semantic("Expected " + parameterTypes.size() + " arguments for constructor '" + className + "' but got " + argumentTypes.size() + ".");
            }
            for (int i = 0; i < argumentTypes.size(); i++) {
                SemanticType parameterType = parameterTypes.get(i);
                SemanticType argumentType = argumentTypes.get(i);
                if (parameterType == null || argumentType == null || argumentType == resolver.nullType) {
                    continue;
                }
                if (parameterType.isAssignableFrom(argumentType)) {
                    continue;
                }
                if (symbol.classInfo().nativeClass && Builtins.ARRAY.equals(className)) {
                    throw BumpException.semantic("Array() expects an Array value, but got " + argumentType.displayName() + ".");
                }
                throw BumpException.semantic("Cannot pass " + argumentType.displayName() + " to argument " + (i + 1) + " of type " + parameterType.displayName() + ".");
            }
            return;
        }
        SemanticType constructorType = resolver.resolveFunctionOverload(className, constructorTypes, argumentTypes, className);
        if (constructorType == null) {
            return;
        }
    }

    private String compoundOperatorMethod(TokenType operator) {
        return switch (operator) {
            case PLUSASSIGN -> "_add";
            case MINUSASSIGN -> "_sub";
            case MULASSIGN -> "_mul";
            case DIVASSIGN -> "_div";
            default -> throw BumpException.internal("Unsupported compound operator: " + operator);
        };
    }

    private String describeType(SemanticType semanticType) {
        return semanticType == null ? "Unknown" : semanticType.displayName();
    }

    private String extractBaseName(String typeName) {
        int ltIndex = typeName.indexOf('<');
        return ltIndex == -1 ? typeName : typeName.substring(0, ltIndex);
    }

    private List<String> extractTypeArguments(String typeName) {
        int ltIndex = typeName.indexOf('<');
        if (ltIndex == -1) {
            return List.of();
        }
        int gtIndex = typeName.lastIndexOf('>');
        if (gtIndex == -1) {
            return List.of();
        }
        String typeArgsString = typeName.substring(ltIndex + 1, gtIndex);
        if (typeArgsString.isEmpty()) {
            return List.of();
        }
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

    private void validateTypeArguments(ClassInfo classInfo, List<String> typeArguments, String fullName) {
        if (typeArguments.isEmpty() && classInfo.typeParameters.isEmpty()) {
            return;
        }
        if (typeArguments.isEmpty() && !classInfo.typeParameters.isEmpty()) {
            throw BumpException.semantic("Class '" + classInfo.name + "' requires " + classInfo.typeParameters.size() + " type argument(s), but none were provided.");
        }
        if (!typeArguments.isEmpty() && classInfo.typeParameters.isEmpty()) {
            throw BumpException.semantic("Class '" + classInfo.name + "' does not accept type arguments.");
        }
        if (typeArguments.size() != classInfo.typeParameters.size()) {
            throw BumpException.semantic("Class '" + classInfo.name + "' requires " + classInfo.typeParameters.size() + " type argument(s), but " + typeArguments.size() + " were provided.");
        }
    }
}
