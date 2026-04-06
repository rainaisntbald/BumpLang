package ast;

public class IfStmt extends Stmt {
    public final Expr condition;
    public final Stmt thenBranch;
    public final Stmt elseBranch;

    public IfStmt(Expr condition, Stmt thenBranch, Stmt elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitIfStmt(this);
    }

    @Override
    public String toStringTree() {
        return "if(" + condition.toStringTree() + ") ...";
    }
}
