package ast;

import java.util.List;

public class BlockStmt extends Stmt {
    public final List<Stmt> statements;
    public BlockStmt(List<Stmt> statements) { this.statements = statements; }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitBlockStmt(this);
    }

    @Override
    public String toStringTree() { return "{ ... }"; }
}
