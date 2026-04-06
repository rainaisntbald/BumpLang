package ast;

import bump.SemanticType;

public class TypeParameter {
    public final String name;
    public final String boundName;
    private final int line;
    private final int column;
    private final int length;
    private SemanticType resolvedBoundType;

    public TypeParameter(String name, String boundName, int line, int column, int length) {
        this.name = name;
        this.boundName = boundName;
        this.line = line;
        this.column = column;
        this.length = length;
    }

    public SemanticType getResolvedBoundType() {
        return resolvedBoundType;
    }

    public void resolveBoundType(SemanticType type) {
        this.resolvedBoundType = type;
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
