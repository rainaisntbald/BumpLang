package ast;

import bump.TokenType;

public class CompoundSetExpr extends Expr {
    public final Expr target;
    public final TokenType operator;
    public final Expr value;
    public final boolean returnsPreviousValue;

    public CompoundSetExpr(Expr target, TokenType operator, Expr value) {
        this(target, operator, value, false);
    }

    public CompoundSetExpr(Expr target, TokenType operator, Expr value, boolean returnsPreviousValue) {
        this.target = target;
        this.operator = operator;
        this.value = value;
        this.returnsPreviousValue = returnsPreviousValue;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitCompoundSetExpr(this);
    }

    @Override
    public String toStringTree() {
        return target.toStringTree() + " " + operatorText() + " " + value.toStringTree();
    }

    private String operatorText() {
        return switch (operator) {
            case PLUSASSIGN -> "+=";
            case MINUSASSIGN -> "-=";
            case MULASSIGN -> "*=";
            case DIVASSIGN -> "/=";
            default -> operator.name();
        };
    }
}
