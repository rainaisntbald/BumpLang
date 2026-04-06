package ast;

import java.util.List;

public class ArrayLiteral extends Expr {
    public final List<Expr> elements;

    public ArrayLiteral(List<Expr> elements) {
        this.elements = elements;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitArrayLiteral(this); }

    @Override
    public String toStringTree() {
        return "ARR[" + elements.size() + "]";
    }
}
