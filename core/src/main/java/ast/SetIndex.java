package ast;

public class SetIndex extends Expr{
    public final Expr array;
    public final Expr index;
    public final Expr value;

    public SetIndex(Expr array, Expr index, Expr value) {
        this.array = array;
        this.index = index;
        this.value = value;
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visitSetIndex(this);
    }

    @Override
    public String toStringTree() {
        return array.toStringTree() + "[" + index.toStringTree() + "] = " + value.toStringTree();
    }
}
