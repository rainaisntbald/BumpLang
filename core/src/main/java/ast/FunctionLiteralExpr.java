package ast;

import bump.SemanticType;
import java.util.List;

public class FunctionLiteralExpr extends Expr {
    public final String returnTypeName;
    public final List<TypeParameter> typeParameters;
    public final List<Parameter> parameters;
    public final List<Stmt> body;
    public final boolean isAbstractMethod;
    private SemanticType resolvedReturnType;

    public FunctionLiteralExpr(String returnTypeName, List<Parameter> parameters, List<Stmt> body) {
        this(returnTypeName, List.of(), parameters, body, false);
    }

    public FunctionLiteralExpr(String returnTypeName, List<Parameter> parameters, List<Stmt> body, boolean isAbstractMethod) {
        this(returnTypeName, List.of(), parameters, body, isAbstractMethod);
    }

    public FunctionLiteralExpr(String returnTypeName, List<TypeParameter> typeParameters, List<Parameter> parameters, List<Stmt> body, boolean isAbstractMethod) {
        this.returnTypeName = returnTypeName;
        this.typeParameters = typeParameters;
        this.parameters = parameters;
        this.body = body;
        this.isAbstractMethod = isAbstractMethod;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitFunctionLiteralExpr(this);
    }

    @Override
    public String toStringTree() {
        String renderedReturnType = returnTypeName == null ? "" : returnTypeName + " ";
        String renderedTypeParameters = typeParameters.isEmpty()
                ? ""
                : "<" + String.join(", ", typeParameters.stream().map(typeParameter -> {
                    if (typeParameter.boundName == null) {
                        return typeParameter.name;
                    }
                    return typeParameter.name + " implements " + typeParameter.boundName;
                }).toList()) + ">";
        String signature = "fun " + renderedReturnType + renderedTypeParameters + "(" + String.join(", ", parameters.stream().map(Parameter::toString).toList()) + ")";
        return isAbstractMethod ? signature + ";" : signature + " { ... }";
    }

    public SemanticType getResolvedReturnType() {
        return resolvedReturnType;
    }

    public void resolveReturnType(SemanticType resolvedReturnType) {
        this.resolvedReturnType = resolvedReturnType;
    }

    public boolean hasSameParameterTypes(FunctionLiteralExpr other) {
        if (parameters.size() != other.parameters.size()) {
            return false;
        }
        for (int i = 0; i < parameters.size(); i++) {
            Parameter left = parameters.get(i);
            Parameter right = other.parameters.get(i);
            SemanticType leftType = left.getResolvedType();
            SemanticType rightType = right.getResolvedType();
            String leftName = leftType != null ? leftType.name() : left.typeName;
            String rightName = rightType != null ? rightType.name() : right.typeName;
            if (!leftName.equals(rightName)) {
                return false;
            }
        }
        return true;
    }
}
