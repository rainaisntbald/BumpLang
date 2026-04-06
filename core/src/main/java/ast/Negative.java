package ast;

public class Negative extends Expr {
    public final Expr expr;
    public Negative(Expr expr) { this.expr = expr; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitNegative(this);
    }

    @Override
    public String toStringTree() {
        return "-" + expr.toStringTree();
    }
}
