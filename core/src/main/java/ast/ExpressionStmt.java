package ast;

public class ExpressionStmt extends Stmt {
    public final Expr expr;

    public ExpressionStmt(Expr expr) {
        this.expr = expr;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitExpressionStmt(this);
    }

    @Override
    public String toStringTree() {
        return expr.toStringTree();
    }
}
