package ast;

public class IntegerLiteral extends Expr {
    public final int value;
    public IntegerLiteral(int value) { this.value = value; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitIntegerLiteral(this);
    }

    @Override
    public String toStringTree() {
        return Integer.toString(value);
    }
}
