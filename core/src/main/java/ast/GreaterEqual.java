package ast;

public class GreaterEqual extends Expr {
    public final Expr left, right;
    public GreaterEqual(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitGreaterEqual(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " >= " + right.toStringTree() + ")";
    }
}
