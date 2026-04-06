package ast;

public class ForStmt extends Stmt {
    public final Stmt initializer;
    public final Expr condition;
    public final Stmt increment;
    public final Stmt body;

    public ForStmt(Stmt initializer, Expr condition, Stmt increment, Stmt body) {
        this.initializer = initializer;
        this.condition = condition;
        this.increment = increment;
        this.body = body;
    }

    @Override
    public void accept(Visitor visitor) { visitor.visitForStmt(this); }

    @Override
    public String toStringTree() {
        String renderedInitializer = initializer == null ? "" : initializer.toStringTree();
        String renderedCondition = condition == null ? "" : condition.toStringTree();
        String renderedIncrement = increment == null ? "" : increment.toStringTree();
        return "for(" + renderedInitializer + "; " + renderedCondition + "; " + renderedIncrement + ") ...";
    }

}
