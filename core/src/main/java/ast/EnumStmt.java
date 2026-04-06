package ast;

import java.util.List;

public class EnumStmt extends Stmt {
    public final String name;
    public final List<TypeParameter> typeParameters;
    public final List<EnumVariant> variants;

    public EnumStmt(String name, List<TypeParameter> typeParameters, List<EnumVariant> variants) {
        this.name = name;
        this.typeParameters = typeParameters;
        this.variants = variants;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitEnumStmt(this);
    }

    @Override
    public String toStringTree() {
        return "enum " + name;
    }
}
