package ast;

public class Sub extends Expr {
    public final Expr left, right;
    public Sub(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitSub(this);
    }

    @Override
    public String toStringTree() {
        return "(" + left.toStringTree() + " - " + right.toStringTree() + ")";
    }
}
