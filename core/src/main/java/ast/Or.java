package ast;

public class Or extends Expr {
    public final Expr left, right;
    public Or(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitOr(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " || " + right.toStringTree() + ")";
    }
}
