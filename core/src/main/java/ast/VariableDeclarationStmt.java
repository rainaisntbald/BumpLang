package ast;

import bump.SemanticType;

public class VariableDeclarationStmt extends Stmt {
    public final String typeName;
    public final String name;
    public final Expr value;
    public final boolean isPrivate;
    private SemanticType resolvedType;

    public VariableDeclarationStmt(String typeName, String name, Expr value) {
        this.typeName = typeName;
        this.name = name;
        this.value = value;
        this.isPrivate = false;
    }

    public VariableDeclarationStmt(String typeName, String name, Expr value, boolean isPrivate) {
        this.typeName = typeName;
        this.name = name;
        this.value = value;
        this.isPrivate = isPrivate;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitVariableDeclarationStmt(this);
    }

    @Override
    public String toStringTree() {
        String renderedValue = value == null ? "" : " = " + value.toStringTree();
        if (typeName != null) {
            return typeName + " " + name + renderedValue;
        }
        return name + renderedValue;
    }

    public SemanticType getResolvedType() {
        return resolvedType;
    }

    public void resolveType(SemanticType type) {
        this.resolvedType = type;
    }
}
