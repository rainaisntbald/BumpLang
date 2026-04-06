package ast;

import java.util.List;

public class ClassStmt extends Stmt {
    public final String name;
    public final boolean isAbstract;
    public final List<TypeParameter> typeParameters;
    public final String superclassName;
    public final List<String> interfaceNames;
    public final List<VariableDeclarationStmt> fields;
    public final List<VariableDeclarationStmt> methods;

    public ClassStmt(String name, boolean isAbstract, List<TypeParameter> typeParameters, String superclassName, List<String> interfaceNames, List<VariableDeclarationStmt> fields, List<VariableDeclarationStmt> methods) {
        this.name = name;
        this.isAbstract = isAbstract;
        this.typeParameters = typeParameters;
        this.superclassName = superclassName;
        this.interfaceNames = interfaceNames;
        this.fields = fields;
        this.methods = methods;
    }

    @Override
    public void accept(Visitor<?> visitor) {
        visitor.visitClassStmt(this);
    }

    @Override
    public String toStringTree() {
        return (isAbstract ? "abstract class " : "class ") + name;
    }
}
