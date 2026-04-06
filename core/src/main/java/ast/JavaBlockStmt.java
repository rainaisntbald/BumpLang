package ast;

public class JavaBlockStmt extends Stmt {
    public final String source;

    public JavaBlockStmt(String source) {
        this.source = source;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitJavaBlockStmt(this);
    }

    @Override
    public String toStringTree() {
        return "java { ... }";
    }
}
