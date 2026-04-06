package ast;

public class Divide extends Expr {
    public final Expr left, right;
    public Divide(Expr left, Expr right) { this.left = left; this.right = right; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitDivide(this);
    }

    @Override
    public String toStringTree(){
        return "(" + left.toStringTree() + " / " + right.toStringTree() + ")";
    }
}
