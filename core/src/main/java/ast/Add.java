package ast;

public class Add extends Expr {
    public final Expr left, right;
    public Add(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitAdd(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " + " + right.toStringTree() + ")";
    }
}
