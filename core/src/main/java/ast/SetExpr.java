package ast;

public class SetExpr extends Expr {
    public final Expr object;
    public final String name;
    public final Expr value;

    public SetExpr(Expr object, String name, Expr value) {
        this.object = object;
        this.name = name;
        this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitSetExpr(this);
    }

    @Override
    public String toStringTree() {
        if (object == null) {
            return name + " = " + value.toStringTree();
        }
        return object.toStringTree() + "." + name + " = " + value.toStringTree();
    }
}
