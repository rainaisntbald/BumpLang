package ast;

public class ContinueStmt extends Stmt {
    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitContinueStmt(this);
    }

    @Override
    public String toStringTree() {
        return "continue";
    }
}
