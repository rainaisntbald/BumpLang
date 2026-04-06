package ast;

public class VariableExpr extends Expr {
    public final String name;
    public VariableExpr(String name) { this.name = name; }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitVariableExpr(this);
    }

    @Override
    public String toStringTree() { return this.name; }
}
