package ast;

public class Modulo extends Expr {
    public final Expr left, right;
    public Modulo(Expr left, Expr right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitModulo(this); }

    @Override
    public String toStringTree() {
        return "(" + left + " % " + right + ")";
    }
}