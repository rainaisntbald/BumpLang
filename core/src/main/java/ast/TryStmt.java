package ast;

public class TryStmt extends Stmt {
    public final BlockStmt tryBlock;
    public final Parameter catchParameter;
    public final BlockStmt catchBlock;
    public final BlockStmt finallyBlock;

    public TryStmt(BlockStmt tryBlock, Parameter catchParameter, BlockStmt catchBlock, BlockStmt finallyBlock) {
        this.tryBlock = tryBlock;
        this.catchParameter = catchParameter;
        this.catchBlock = catchBlock;
        this.finallyBlock = finallyBlock;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitTryStmt(this);
    }

    @Override
    public String toStringTree() {
        if (catchBlock != null && finallyBlock != null) {
            return "try { ... } " + catchSummary() + " finally { ... }";
        }
        if (catchBlock != null) {
            return "try { ... } " + catchSummary();
        }
        return "try { ... } finally { ... }";
    }

    private String catchSummary() {
        if (catchParameter == null) {
            return "catch { ... }";
        }
        return "catch(" + catchParameter + ") { ... }";
    }
}
