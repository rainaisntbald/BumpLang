package ast;

import java.util.List;
import java.util.stream.Collectors;

public class CallExpr extends Expr {
    public final Expr callee;
    public final List<String> typeArguments;
    public final List<Expr> arguments;

    public CallExpr(Expr callee, List<String> typeArguments, List<Expr> arguments) {
        this.callee = callee;
        this.typeArguments = typeArguments;
        this.arguments = arguments;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitCallExpr(this);
    }

    @Override
    public String toStringTree() {
        return callee.toStringTree() + "(" + arguments.stream().map(Expr::toStringTree).collect(Collectors.joining(", ")) + ")";
    }
}
