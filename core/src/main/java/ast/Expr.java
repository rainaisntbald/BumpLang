package ast;

import bump.Token;

public abstract class Expr {
    private int line = -1;
    private int column = -1;
    private int length = 1;

    public abstract <R> R accept(Visitor<R> visitor);
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
    public <T extends Expr> T at(int line) {
        this.line = line;
        this.column = 1;
        this.length = 1;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Expr> T at(Token token) {
        if (token != null) {
            this.line = token.getLine();
            this.column = token.getColumn();
            this.length = Math.max(1, token.getSourceLength());
        }
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Expr> T at(int line, int column, int length) {
        this.line = line;
        this.column = column;
        this.length = Math.max(1, length);
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Expr> T at(Expr other) {
        if (other != null && other.getLine() > 0) {
            this.line = other.getLine();
            this.column = other.getColumn();
            this.length = other.getLength();
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
