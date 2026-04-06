package ast;

import java.util.List;

public class SwitchCase {
    public final List<Expr> labels;
    public final List<Stmt> body;
    public final boolean isDefault;

    public SwitchCase(List<Expr> labels, List<Stmt> body, boolean isDefault) {
        this.labels = labels;
        this.body = body;
        this.isDefault = isDefault;
    }
}
