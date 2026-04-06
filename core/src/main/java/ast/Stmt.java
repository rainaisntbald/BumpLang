package ast;

import bump.Token;

public abstract class Stmt {
    private int line = -1;
    private int column = -1;
    private int length = 1;

    public abstract void accept(Visitor<?> visitor);
    public abstract String toStringTree();

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getLength() {
        return length;
    }

    @SuppressWarnings("unchecked")
    public <T extends Stmt> T at(int line) {
        this.line = line;
        this.column = 1;
        this.length = 1;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Stmt> T at(Token token) {
        if (token != null) {
            this.line = token.getLine();
            this.column = token.getColumn();
            this.length = Math.max(1, token.getSourceLength());
        }
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Stmt> T at(int line, int column, int length) {
        this.line = line;
        this.column = column;
        this.length = Math.max(1, length);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Stmt> T at(Expr expr) {
        if (expr != null && expr.getLine() > 0) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.length = expr.getLength();
        }
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Stmt> T at(Stmt stmt) {
        if (stmt != null && stmt.getLine() > 0) {
            this.line = stmt.getLine();
            this.column = stmt.getColumn();
            this.length = stmt.getLength();
        }
        return (T) this;
    }

    public String describeLocation() {
        String summary = toStringTree().replaceAll("\\s+", " ").trim();
        if (summary.length() > 80) {
            return summary.substring(0, 77) + "...";
        }
        return summary;
    }
}
