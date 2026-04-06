package ast;

import java.util.List;

public class SwitchStmt extends Stmt {
    public final Expr expression;
    public final List<SwitchCase> cases;

    public SwitchStmt(Expr expression, List<SwitchCase> cases) {
        this.expression = expression;
        this.cases = cases;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitSwitchStmt(this);
    }

    @Override
    public String toStringTree() {
        return "switch(" + expression.toStringTree() + ") ...";
    }
}
