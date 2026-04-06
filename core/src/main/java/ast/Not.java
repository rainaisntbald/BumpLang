package ast;

public class Not extends Expr {
    public final Expr expr;
    public Not(Expr expr) { this.expr = expr; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitNot(this);
    }

    @Override
    public String toStringTree() {
        return "!" + expr.toStringTree();
    }
}
