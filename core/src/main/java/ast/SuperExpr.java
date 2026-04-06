package ast;

import bump.Token;

public class SuperExpr extends Expr {
    public final Token keyword;
    public final Token member;

    public SuperExpr(Token keyword, Token member) {
        this.keyword = keyword;
        this.member = member;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visitSuperExpr(this);
    }

    @Override
    public String toStringTree() { return "super." + member.getText(); }
}
