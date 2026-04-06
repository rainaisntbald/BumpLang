package ast;

public class WhileStmt extends Stmt {
    public final Expr condition;
    public final Stmt block;

    public WhileStmt(Expr condition, Stmt block) { this.condition = condition; this.block = block; }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitWhileStmt(this);
    }

    @Override
    public String toStringTree() {
        return "while(" + condition.toStringTree() + ") ...";
    }
}
