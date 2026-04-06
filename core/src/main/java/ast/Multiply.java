package ast;

public class Multiply extends Expr {
    public final Expr left, right;
    public Multiply(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitMultiply(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " * " + right.toStringTree() + ")";
    }
}
