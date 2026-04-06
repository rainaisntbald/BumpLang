package bump;

import ast.BlockStmt;
import ast.ClassStmt;
import ast.ExpressionStmt;
import ast.ExtendStmt;
import ast.EnumStmt;
import ast.Expr;
import ast.ForStmt;
import ast.FunctionLiteralExpr;
import ast.GetExpr;
import ast.IfStmt;
import ast.JavaBlockStmt;
import ast.ReturnStmt;
import ast.Stmt;
import ast.VariableExpr;
import ast.CallExpr;
import ast.SwitchCase;
import ast.SwitchStmt;
import ast.TryStmt;
import ast.VariableDeclarationStmt;
import ast.WhileStmt;

import java.util.List;

final class ResolverStatementAnalyzer {
    private final Resolver resolver;
    private record EnumCasePattern(String variantName, String bindingName, SemanticType bindingType) {}

    ResolverStatementAnalyzer(Resolver resolver) {
        this.resolver = resolver;
    }

    void visitBlockStmt(BlockStmt stmt) {
        resolver.beginScope();
        try {
            for (int i = 0; i < stmt.statements.size(); i++) {
                resolver.ensureFunctionOverloadsDeclared(stmt.statements, i);
                resolver.resolveStatement(stmt.statements.get(i));
            }
        } finally {
            resolver.endScope();
        }
    }

    void visitWhileStmt(WhileStmt stmt) {
        resolver.expressionAnalyzer.analyzeRequiredType(stmt.condition, resolver.boolType, "Expected a Bool value, but got %s.");
        resolver.breakableDepth++;
        resolver.continueDepth++;
        try {
            resolver.resolveStatement(stmt.block);
        } finally {
            resolver.breakableDepth--;
            resolver.continueDepth--;
        }
    }

    void visitForStmt(ForStmt stmt) {
        resolver.beginScope();
        try {
            if (stmt.initializer != null) {
                resolver.resolveStatement(stmt.initializer);
            }
            if (stmt.condition != null) {
                resolver.expressionAnalyzer.analyzeRequiredType(stmt.condition, resolver.boolType, "Expected a Bool value, but got %s.");
            }
            if (stmt.increment != null) {
                resolver.resolveStatement(stmt.increment);
            }
            resolver.breakableDepth++;
            resolver.continueDepth++;
            try {
                resolver.resolveStatement(stmt.body);
            } finally {
                resolver.breakableDepth--;
                resolver.continueDepth--;
            }
        } finally {
            resolver.endScope();
        }
    }

    void visitIfStmt(IfStmt stmt) {
        resolver.expressionAnalyzer.analyzeRequiredType(stmt.condition, resolver.boolType, "Expected a Bool value, but got %s.");
        resolver.resolveStatement(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolver.resolveStatement(stmt.elseBranch);
        }
    }

    void visitVariableDeclarationStmt(VariableDeclarationStmt stmt) {
        boolean functionDeclaration = "Function".equals(stmt.typeName) && stmt.value instanceof FunctionLiteralExpr functionLiteral;
        if (!functionDeclaration) {
            resolver.declare(stmt.name);
        }
        if (stmt.typeName != null) {
            stmt.resolveType(resolver.resolveTypeName(stmt.typeName, "Type '" + stmt.typeName + "' is not a class."));
        }
        if (!functionDeclaration) {
            resolver.setCurrentSymbol(stmt.name, SymbolKind.VARIABLE, stmt.getResolvedType(), null);
        }
        if (stmt.value != null) {
            SemanticType valueType = resolver.expressionAnalyzer.analyze(stmt.value);
            if (!functionDeclaration) {
                resolver.expressionAnalyzer.validateAssignmentCompatibility(stmt.getResolvedType(), valueType, "variable '" + stmt.name + "'");
            }
            if (!functionDeclaration && "Function".equals(stmt.typeName) && valueType != null && Builtins.FUNCTION.equals(valueType.name())) {
                resolver.setCurrentSymbol(stmt.name, SymbolKind.VARIABLE, valueType, null);
            }
        }
        if (!functionDeclaration) {
            resolver.define(stmt.name);
        }
    }

    void visitReturnStmt(ReturnStmt stmt) {
        if (resolver.currentFunction == FunctionType.NONE) {
            throw BumpException.semantic("Cannot return from top-level code.");
        }
        if (resolver.currentFunction == FunctionType.INITIALIZER && stmt.value != null) {
            throw BumpException.semantic("Initializers cannot return a value.");
        }
        if (resolver.currentFunction != FunctionType.INITIALIZER && resolver.currentReturnType == resolver.nullType) {
            if (stmt.value != null) {
                throw BumpException.semantic("Function with return type Null cannot return a value.");
            }
            return;
        }
        if (resolver.currentFunction != FunctionType.INITIALIZER
                && resolver.currentReturnType != null
                && stmt.value == null) {
            throw BumpException.semantic("Function with return type " + resolver.currentReturnType.name() + " must return a value.");
        }
        if (stmt.value != null) {
            if (resolver.currentFunction != FunctionType.INITIALIZER && resolver.currentReturnType != null) {
                SemanticType valueType = resolver.expressionAnalyzer.analyze(stmt.value);
                if (valueType != null
                        && valueType != resolver.nullType
                        && !resolver.currentReturnType.isAssignableFrom(valueType)) {
                    throw BumpException.semantic("Cannot assign " + valueType.name() + " to return value of type " + resolver.currentReturnType.name() + ".");
                }
            } else {
                resolver.expressionAnalyzer.analyze(stmt.value);
            }
        }
    }

    void visitBreakStmt(ast.BreakStmt stmt) {
        if (resolver.breakableDepth == 0) {
            throw BumpException.semantic("Cannot use 'break' outside of a loop.");
        }
    }

    void visitContinueStmt(ast.ContinueStmt stmt) {
        if (resolver.continueDepth == 0) {
            throw BumpException.semantic("Cannot use 'continue' outside of a loop.");
        }
    }

    void visitSwitchStmt(SwitchStmt stmt) {
        SemanticType switchType = resolver.expressionAnalyzer.analyze(stmt.expression);
        ClassInfo switchClassInfo = resolver.classInfoForType(switchType);
        if (switchClassInfo != null && !switchClassInfo.enumValues.isEmpty()) {
            visitEnumSwitchStmt(stmt, switchType, switchClassInfo);
            return;
        }
        resolver.breakableDepth++;
        try {
            for (SwitchCase switchCase : stmt.cases) {
                for (Stmt bodyStmt : switchCase.body) {
                    resolver.resolveStatement(bodyStmt);
                }
            }
        } finally {
            resolver.breakableDepth--;
        }
    }

    private void visitEnumSwitchStmt(SwitchStmt stmt, SemanticType switchType, ClassInfo enumClassInfo) {
        resolver.breakableDepth++;
        try {
            java.util.Set<String> covered = new java.util.HashSet<>();
            boolean hasDefault = false;
            for (SwitchCase switchCase : stmt.cases) {
                EnumCasePattern pattern = null;
                if (switchCase.isDefault) {
                    hasDefault = true;
                } else {
                    if (switchCase.labels.size() != 1) {
                        throw BumpException.semantic("Enum switch cases must declare exactly one label.");
                    }
                    pattern = resolveEnumCasePattern(switchCase.labels.get(0), switchType, enumClassInfo);
                    if (!covered.add(pattern.variantName)) {
                        throw BumpException.semantic("Duplicate enum case '" + pattern.variantName + "' in switch.");
                    }
                }

                resolver.beginScope();
                try {
                    if (pattern != null && pattern.bindingName != null) {
                        resolver.declare(pattern.bindingName);
                        resolver.setCurrentSymbol(pattern.bindingName, SymbolKind.VARIABLE, pattern.bindingType, null);
                        resolver.define(pattern.bindingName);
                    }
                    for (Stmt bodyStmt : switchCase.body) {
                        resolver.resolveStatement(bodyStmt);
                    }
                } finally {
                    resolver.endScope();
                }
            }

            if (!hasDefault) {
                java.util.List<String> missing = enumClassInfo.enumValues.keySet().stream()
                        .filter(variant -> !covered.contains(variant))
                        .sorted()
                        .toList();
                if (!missing.isEmpty()) {
                    throw BumpException.semantic("Switch over enum '" + enumClassInfo.name + "' is not exhaustive. Missing case '" + missing.get(0) + "'.");
                }
            }
        } finally {
            resolver.breakableDepth--;
        }
    }

    private EnumCasePattern resolveEnumCasePattern(Expr label, SemanticType switchType, ClassInfo enumClassInfo) {
        VariantRef variantRef = extractVariantRef(label);
        if (variantRef == null) {
            throw BumpException.semantic("Expected enum variant label in switch case.");
        }
        if (variantRef.enumTypeName != null && !rawTypeName(variantRef.enumTypeName).equals(enumClassInfo.name)) {
            throw BumpException.semantic("Case label references enum '" + variantRef.enumTypeName + "', expected '" + enumClassInfo.name + "'.");
        }
        EnumVariantInfo variantInfo = enumClassInfo.enumValues.get(variantRef.variantName);
        if (variantInfo == null) {
            throw BumpException.semantic("Enum '" + enumClassInfo.name + "' has no variant '" + variantRef.variantName + "'.");
        }
        if (variantRef.bindingName != null && variantInfo.payloadName == null) {
            throw BumpException.semantic("Enum variant '" + variantRef.variantName + "' has no payload.");
        }
        SemanticType bindingType = null;
        if (variantRef.bindingName != null) {
            bindingType = resolver.bindFieldType(enumClassInfo, switchType, variantInfo.payloadName);
            if (bindingType == null) {
                bindingType = variantInfo.payloadType == null ? resolver.objectType : variantInfo.payloadType;
            }
        }
        return new EnumCasePattern(variantRef.variantName, variantRef.bindingName, bindingType);
    }

    private record VariantRef(String enumTypeName, String variantName, String bindingName) {}

    private VariantRef extractVariantRef(Expr label) {
        if (label instanceof VariableExpr variableExpr) {
            return new VariantRef(null, variableExpr.name, null);
        }
        if (label instanceof GetExpr getExpr && getExpr.object instanceof VariableExpr enumExpr) {
            return new VariantRef(enumExpr.name, getExpr.name, null);
        }
        if (label instanceof CallExpr callExpr) {
            if (!callExpr.typeArguments.isEmpty()) {
                throw BumpException.semantic("Enum case patterns do not support call type arguments.");
            }
            VariantRef calleeRef;
            if (callExpr.callee instanceof VariableExpr variableExpr) {
                calleeRef = new VariantRef(null, variableExpr.name, null);
            } else if (callExpr.callee instanceof GetExpr getExpr && getExpr.object instanceof VariableExpr enumExpr) {
                calleeRef = new VariantRef(enumExpr.name, getExpr.name, null);
            } else {
                return null;
            }
            if (callExpr.arguments.isEmpty()) {
                return new VariantRef(calleeRef.enumTypeName, calleeRef.variantName, null);
            }
            if (callExpr.arguments.size() != 1 || !(callExpr.arguments.get(0) instanceof VariableExpr bindingVar)) {
                throw BumpException.semantic("Enum case payload pattern must bind a single variable like Some(x).");
            }
            return new VariantRef(calleeRef.enumTypeName, calleeRef.variantName, bindingVar.name);
        }
        return null;
    }

    void visitTryStmt(TryStmt stmt) {
        resolver.resolveStatement(stmt.tryBlock);
        if (stmt.catchBlock != null) {
            if (stmt.catchParameter != null) {
                stmt.catchParameter.resolveType(resolver.resolveDeclaredType(
                        stmt.catchParameter.typeName,
                        "Type '" + stmt.catchParameter.typeName + "' is not a class."
                ));
                if (!resolver.exceptionType.isAssignableFrom(stmt.catchParameter.getResolvedType())) {
                    throw BumpException.semantic("Catch parameter type '" + stmt.catchParameter.typeName + "' must extend Exception.");
                }
                resolver.beginScope();
                try {
                    resolver.declare(stmt.catchParameter.name);
                    resolver.setCurrentSymbol(
                            stmt.catchParameter.name,
                            SymbolKind.VARIABLE,
                            stmt.catchParameter.getResolvedType(),
                            resolver.classInfoForType(stmt.catchParameter.getResolvedType())
                    );
                    resolver.define(stmt.catchParameter.name);
                    for (Stmt catchStmt : stmt.catchBlock.statements) {
                        resolver.resolveStatement(catchStmt);
                    }
                } finally {
                    resolver.endScope();
                }
            } else {
                resolver.resolveStatement(stmt.catchBlock);
            }
        }
        if (stmt.finallyBlock != null) {
            resolver.resolveStatement(stmt.finallyBlock);
        }
    }

    void visitClassStmt(ClassStmt stmt) {
        ClassType enclosingClass = resolver.currentClass;
        ClassInfo enclosingClassInfo = resolver.currentClassInfo;
        resolver.declare(stmt.name);
        resolver.beginTypeParameterScope(stmt.typeParameters);

        try {
            ClassInfo superclassInfo = resolver.classInfoForType(
                    resolver.resolveTypeName(Builtins.OBJECT, "Type 'Object' is not a class.")
            );
            if (stmt.superclassName != null) {
                String superclassRawName = rawTypeName(stmt.superclassName);
                if (superclassRawName.equals(stmt.name)) {
                    throw BumpException.semantic("A class cannot inherit from itself.");
                }
                SemanticType superclassType = resolver.resolveTypeName(stmt.superclassName, "Superclass '" + stmt.superclassName + "' is not a class.");
                superclassInfo = resolver.classInfoForType(superclassType);
                if (superclassInfo == null) {
                    throw BumpException.semantic("Superclass '" + stmt.superclassName + "' is not a class.");
                }
                if (!superclassInfo.extendable) {
                    throw BumpException.semantic("Cannot extend class '" + stmt.superclassName + "'.");
                }
            }
            List<ClassInfo> interfaces = new java.util.ArrayList<>();
            List<SemanticType> interfaceTypes = new java.util.ArrayList<>();
            for (String interfaceName : stmt.interfaceNames) {
                SemanticType interfaceType = resolver.resolveTypeName(interfaceName, "Implemented interface '" + interfaceName + "' is not a class.");
                ClassInfo interfaceInfo = resolver.classInfoForType(interfaceType);
                if (interfaceInfo == null) {
                    throw BumpException.semantic("Implemented interface '" + interfaceName + "' is not a class.");
                }
                if (!interfaceInfo.abstractClass) {
                    throw BumpException.semantic("Implemented interface '" + interfaceName + "' must be an abstract class.");
                }
                if (interfaces.stream().anyMatch(existing -> existing.name.equals(interfaceInfo.name))) {
                    throw BumpException.semantic("Interface '" + interfaceName + "' is listed more than once.");
                }
                interfaces.add(interfaceInfo);
                interfaceTypes.add(interfaceType);
            }

            List<SemanticType> classTypeParameters = stmt.typeParameters.stream()
                    .map(typeParameter -> resolver.lookupTypeParameter(typeParameter.name))
                    .toList();
            SemanticType semanticType = new SemanticType(stmt.name, false, true, superclassInfo.semanticType, interfaceTypes);
            ClassInfo classInfo = new ClassInfo(stmt.name, false, true, stmt.isAbstract, classTypeParameters, superclassInfo, interfaces, semanticType);
            resolver.defineSymbol(stmt.name, SymbolKind.CLASS, semanticType, classInfo);
            resolver.define(stmt.name);

            resolver.currentClass = stmt.superclassName == null ? ClassType.CLASS : ClassType.SUBCLASS;
            resolver.currentClassInfo = classInfo;
            try {
                if (stmt.superclassName != null) {
                    resolver.beginScope();
                    resolver.setCurrentSymbol("super", SymbolKind.VARIABLE, superclassInfo.semanticType, superclassInfo);
                    resolver.define("super");
                }
                resolver.beginScope();
                SemanticType thisType = classTypeParameters.isEmpty()
                        ? classInfo.semanticType
                        : classInfo.semanticType.withTypeArguments(classTypeParameters);
                resolver.setCurrentSymbol("this", SymbolKind.VARIABLE, thisType, classInfo);
                resolver.define("this");
                resolveFields(stmt, classInfo, superclassInfo);
                resolveMethodSignatures(stmt, classInfo, superclassInfo);
                for (VariableDeclarationStmt method : stmt.methods) {
                    FunctionLiteralExpr function = (FunctionLiteralExpr) method.value;
                    if (function.isAbstractMethod) {
                        continue;
                    }
                    FunctionType methodType = method.name.equals("init")
                            ? FunctionType.INITIALIZER
                            : FunctionType.METHOD;
                    resolver.resolveFunction(function, methodType);
                }
                if (!stmt.isAbstract) {
                    List<String> unresolvedMethods = classInfo.unresolvedAbstractMethods();
                    if (!unresolvedMethods.isEmpty()) {
                        throw BumpException.semantic("Class '" + classInfo.name + "' must implement abstract method '" + unresolvedMethods.get(0) + "'.");
                    }
                }
            } finally {
                resolver.endScope();
                if (stmt.superclassName != null) {
                    resolver.endScope();
                }
                resolver.currentClass = enclosingClass;
                resolver.currentClassInfo = enclosingClassInfo;
            }
        } finally {
            resolver.endTypeParameterScope(stmt.typeParameters);
        }
    }

    void visitEnumStmt(EnumStmt stmt) {
        resolver.declare(stmt.name);
        resolver.beginTypeParameterScope(stmt.typeParameters);
        try {
            ClassInfo superclassInfo = resolver.classInfoForType(
                    resolver.resolveTypeName(Builtins.OBJECT, "Type 'Object' is not a class.")
            );
            List<SemanticType> enumTypeParameters = stmt.typeParameters.stream()
                    .map(typeParameter -> resolver.lookupTypeParameter(typeParameter.name))
                    .toList();
            SemanticType semanticType = new SemanticType(stmt.name, false, false, superclassInfo.semanticType, List.of());
            ClassInfo classInfo = new ClassInfo(stmt.name, false, false, false, enumTypeParameters, superclassInfo, List.of(), semanticType);
            classInfo.fields.put("name", resolver.stringType);
            classInfo.fields.put("ordinal", resolver.integerType);
            classInfo.fields.put("value", resolver.objectType);
            resolver.defineSymbol(stmt.name, SymbolKind.CLASS, semanticType, classInfo);
            resolver.define(stmt.name);

            if (stmt.variants.isEmpty()) {
                throw BumpException.semantic("Enum '" + stmt.name + "' must declare at least one variant.");
            }
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            for (ast.EnumVariant variant : stmt.variants) {
                SemanticType payloadType = null;
                if (variant.payloadName != null) {
                    payloadType = variant.payloadTypeName == null
                            ? resolver.objectType
                            : resolver.resolveTypeName(variant.payloadTypeName, "Type '" + variant.payloadTypeName + "' is not a class.");
                    classInfo.fields.put(variant.payloadName, payloadType);
                }
                if (variant.attachedValue != null) {
                    resolver.expressionAnalyzer.analyze(variant.attachedValue);
                }
                if (!seen.add(variant.name)) {
                    throw BumpException.semantic("Enum '" + stmt.name + "' has duplicate variant '" + variant.name + "'.");
                }
                classInfo.enumValues.put(variant.name, new EnumVariantInfo(semanticType, variant.payloadName, payloadType));
            }
        } finally {
            resolver.endTypeParameterScope(stmt.typeParameters);
        }
    }

    void visitExtendStmt(ExtendStmt stmt) {
        SymbolInfo classSymbol = resolver.lookupSymbol(stmt.className);
        if (classSymbol == null
                || classSymbol.kind() != SymbolKind.CLASS
                || classSymbol.classInfo() == null) {
            throw BumpException.semantic("Type '" + stmt.className + "' is not a class.");
        }

        ClassType enclosingClass = resolver.currentClass;
        ClassInfo enclosingClassInfo = resolver.currentClassInfo;
        ClassInfo classInfo = classSymbol.classInfo();
        resolver.currentClass = classInfo.superclass == null ? ClassType.CLASS : ClassType.SUBCLASS;
        resolver.currentClassInfo = classInfo;
        resolver.beginSemanticTypeParameterScope(classInfo.typeParameters);
        try {
            if (classInfo.superclass != null) {
                resolver.beginScope();
                resolver.setCurrentSymbol("super", SymbolKind.VARIABLE, classInfo.superclass.semanticType, classInfo.superclass);
                resolver.define("super");
            }
            resolver.beginScope();
            SemanticType thisType = classInfo.typeParameters.isEmpty()
                    ? classInfo.semanticType
                    : classInfo.semanticType.withTypeArguments(classInfo.typeParameters);
            resolver.setCurrentSymbol("this", SymbolKind.VARIABLE, thisType, classInfo);
            resolver.define("this");
            resolveExtensionMethodSignatures(stmt, classInfo);
            for (VariableDeclarationStmt method : stmt.methods) {
                FunctionLiteralExpr function = (FunctionLiteralExpr) method.value;
                if (function.isAbstractMethod) {
                    throw BumpException.semantic("Extensions cannot declare abstract methods.");
                }
                FunctionType methodType = method.name.equals("init")
                        ? FunctionType.INITIALIZER
                        : FunctionType.METHOD;
                resolver.resolveFunction(function, methodType);
            }
        } finally {
            resolver.endScope();
            if (classInfo.superclass != null) {
                resolver.endScope();
            }
            resolver.endSemanticTypeParameterScope(classInfo.typeParameters);
            resolver.currentClass = enclosingClass;
            resolver.currentClassInfo = enclosingClassInfo;
        }
    }

    void visitExpressionStmt(ExpressionStmt stmt) {
        resolver.resolveExpression(stmt.expr);
    }

    void visitJavaBlockStmt(JavaBlockStmt stmt) {
    }

    private void resolveFields(ClassStmt stmt, ClassInfo classInfo, ClassInfo superclassInfo) {
        for (VariableDeclarationStmt field : stmt.fields) {
            if (classInfo.fields.containsKey(field.name)) {
                throw BumpException.semantic("Field '" + field.name + "' is already defined in this class.");
            }
            if (classInfo.methods.containsKey(field.name)) {
                throw BumpException.semantic("Field '" + field.name + "' conflicts with method in this class.");
            }
            if (superclassInfo != null && (superclassInfo.hasField(field.name) || superclassInfo.hasMethod(field.name))) {
                throw BumpException.semantic("Field '" + field.name + "' is already defined in the superclass.");
            }
            field.resolveType(resolver.resolveTypeName(field.typeName, "Type '" + field.typeName + "' is not a class."));
            if (field.value != null) {
                SemanticType valueType = resolver.expressionAnalyzer.analyze(field.value);
                resolver.expressionAnalyzer.validateFieldAssignmentCompatibility(field.getResolvedType(), valueType, field.name, classInfo.name);
            }
            classInfo.fields.put(field.name, field.getResolvedType());
            classInfo.privateFields.put(field.name, field.isPrivate);
        }
    }

    private void resolveMethodSignatures(ClassStmt stmt, ClassInfo classInfo, ClassInfo superclassInfo) {
        for (VariableDeclarationStmt method : stmt.methods) {
            List<SemanticType> overloads = classInfo.methods.computeIfAbsent(method.name, key -> new java.util.ArrayList<>());
            if (classInfo.fields.containsKey(method.name)) {
                throw BumpException.semantic("Method '" + method.name + "' conflicts with field in this class.");
            }
            if (superclassInfo != null && superclassInfo.hasField(method.name)) {
                throw BumpException.semantic("Method '" + method.name + "' conflicts with field in superclass.");
            }
            FunctionLiteralExpr function = (FunctionLiteralExpr) method.value;
            if (function.isAbstractMethod && method.name.equals("init")) {
                throw BumpException.semantic("Initializers cannot be abstract.");
            }
            if (function.isAbstractMethod && !stmt.isAbstract) {
                throw BumpException.semantic("Only abstract classes can declare abstract methods.");
            }
            SemanticType signature = resolver.functionValueType(function);
            Boolean existingVisibility = classInfo.privateMethods.get(method.name);
            if (existingVisibility != null && existingVisibility != method.isPrivate) {
                throw BumpException.semantic("All overloads of method '" + method.name + "' must use the same visibility.");
            }
            if (overloads.stream().anyMatch(existing -> Resolver.sameSignature(existing, signature))) {
                throw BumpException.semantic("Method '" + method.name + "' already has an overload with the same parameter types.");
            }
            overloads.add(signature);
            classInfo.privateMethods.putIfAbsent(method.name, method.isPrivate);
            if (function.isAbstractMethod) {
                classInfo.abstractMethods.computeIfAbsent(method.name, key -> new java.util.ArrayList<>()).add(signature);
            } else {
                classInfo.concreteMethods.computeIfAbsent(method.name, key -> new java.util.ArrayList<>()).add(signature);
            }
        }
    }

    private void resolveExtensionMethodSignatures(ExtendStmt stmt, ClassInfo classInfo) {
        for (VariableDeclarationStmt method : stmt.methods) {
            if (classInfo.hasField(method.name)) {
                throw BumpException.semantic("Method '" + method.name + "' conflicts with field in class '" + classInfo.name + "'.");
            }
            FunctionLiteralExpr function = (FunctionLiteralExpr) method.value;
            SemanticType signature = resolver.functionValueType(function);
            List<SemanticType> existingOverloads = classInfo.methodOverloads(method.name);
            Boolean existingVisibility = classInfo.privateMethods.get(method.name);
            if (existingVisibility != null && existingVisibility != method.isPrivate) {
                throw BumpException.semantic("All overloads of method '" + method.name + "' must use the same visibility.");
            }
            if (existingOverloads.stream().anyMatch(existing -> Resolver.sameSignature(existing, signature))) {
                throw BumpException.semantic("Method '" + method.name + "' already has an overload with the same parameter types.");
            }
            classInfo.methods.computeIfAbsent(method.name, key -> new java.util.ArrayList<>()).add(signature);
            classInfo.privateMethods.putIfAbsent(method.name, method.isPrivate);
        }
    }

    private String rawTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart < 0 ? typeName : typeName.substring(0, genericStart);
    }
}
