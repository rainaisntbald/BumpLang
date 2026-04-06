package ast;

public class Greater extends Expr {
    public final Expr left, right;
    public Greater(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitGreater(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " > " + right.toStringTree() + ")";
    }
}
