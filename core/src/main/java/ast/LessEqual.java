package ast;

public class LessEqual extends Expr {
    public final Expr left, right;
    public LessEqual(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitLessEqual(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " <= " + right.toStringTree() + ")";
    }
}
