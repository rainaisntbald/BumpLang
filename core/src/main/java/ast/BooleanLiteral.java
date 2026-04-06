package ast;

public class BooleanLiteral extends Expr {
    public final boolean value;
    public BooleanLiteral(boolean value) { this.value = value; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitBooleanLiteral(this);
    }

    @Override
    public String toStringTree() {
        return String.valueOf(value);
    }
}
