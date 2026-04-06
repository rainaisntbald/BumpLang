package ast;

public class And extends Expr {
    public final Expr left, right;
    public And(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitAnd(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " && " + right.toStringTree() + ")";
    }
}
