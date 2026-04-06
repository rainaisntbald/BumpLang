package ast;

public class GetExpr extends Expr {
    public final Expr object;
    public final String name;

    public GetExpr(Expr object, String name) {
        this.object = object;
        this.name = name;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitGetExpr(this);
    }

    @Override
    public String toStringTree() {
        return object.toStringTree() + "." + name;
    }
}
