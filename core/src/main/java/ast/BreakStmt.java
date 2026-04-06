package ast;

public class BreakStmt extends Stmt {
    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitBreakStmt(this);
    }

    @Override
    public String toStringTree() {
        return "break";
    }
}
