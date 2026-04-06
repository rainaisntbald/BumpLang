package ast;

import java.util.List;

public class ExtendStmt extends Stmt {
    public final String className;
    public final List<VariableDeclarationStmt> methods;

    public ExtendStmt(String className, List<VariableDeclarationStmt> methods) {
        this.className = className;
        this.methods = methods;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitExtendStmt(this);
    }

    @Override
    public String toStringTree() {
        return "extend " + className;
    }
}
