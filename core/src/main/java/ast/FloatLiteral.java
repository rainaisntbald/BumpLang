package ast;

public class FloatLiteral extends Expr {
    public final double value;

    public FloatLiteral(double value) {
        this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitFloatLiteral(this);
    }

    @Override
    public String toStringTree() {
        return Double.toString(value);
    }
}
