package ast;

import bump.SemanticType;

public class Parameter {
    public final String typeName;
    public final String name;
    private final int line;
    private final int column;
    private final int length;
    private SemanticType resolvedType;

    public Parameter(String typeName, String name, int line, int column, int length) {
        this.typeName = typeName;
        this.name = name;
        this.line = line;
        this.column = column;
        this.length = length;
    }

    @Override
    public String toString() {
        return typeName + " " + name;
    }

    public SemanticType getResolvedType() {
        return resolvedType;
    }

    public void resolveType(SemanticType type) {
        this.resolvedType = type;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getLength() {
        return length;
    }
}
