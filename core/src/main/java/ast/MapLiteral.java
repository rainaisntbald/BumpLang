package ast;

import java.util.List;

public class MapLiteral extends Expr {
    public final List<Expr> keys;
    public final List<Expr> values;

    public MapLiteral(List<Expr> keys, List<Expr> values) {
        this.keys = keys;
        this.values = values;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitMapLiteral(this);
    }

    @Override
    public String toStringTree() {
        return "MAP[" + keys.size() + "]";
    }
}
