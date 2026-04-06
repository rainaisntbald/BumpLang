package ast;

public class Less extends Expr {
    public final Expr left, right;
    public Less(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitLess(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " < " + right.toStringTree() + ")";
    }
}
