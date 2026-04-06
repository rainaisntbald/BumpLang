package ast;

public class ReturnStmt extends Stmt {
    public final Expr value;

    public ReturnStmt(Expr value) {
        this.value = value;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitReturnStmt(this);
    }

    @Override
    public String toStringTree() {
        return "return " + (value != null ? value.toStringTree() : "");
    }
}
