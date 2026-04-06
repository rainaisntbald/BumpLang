package ast;

public class NullLiteral extends Expr {
    public NullLiteral() {}

    @Override
    public <R>  R accept(Visitor<R> visitor) {
        return visitor.visitNullLiteral(this);
    }

    @Override
    public String toStringTree() {
        return "NULL";
    }
}
