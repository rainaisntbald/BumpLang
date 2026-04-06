package ast;

public class Equality extends Expr {
    public final Expr left, right;
    public Equality(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitEquality(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " == " + right.toStringTree() + ")";
    }
}
