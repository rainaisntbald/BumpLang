package ast;

public class StringLiteral extends Expr {
    public final String value;
    public StringLiteral(String value) { this.value = value; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitStringLiteral(this);
    }

    @Override
    public String toStringTree() {
        return "\"" + value + "\"";
    }
}
