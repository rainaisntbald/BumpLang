package ast;

public class ArrayIndex extends Expr{
    public final Expr array;
    public final Expr index;

    public ArrayIndex(Expr array, Expr index) {
        this.array = array;
        this.index = index;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitArrayIndex(this); }

    @Override
    public String toStringTree() {
        return array.toStringTree() + "[" + index.toStringTree() + "]";
    }
}
