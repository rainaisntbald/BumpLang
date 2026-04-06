package ast;

public class Inequality extends Expr {
    public final Expr left, right;
    public Inequality(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitInequality(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " != " + right.toStringTree() + ")";
    }
}
