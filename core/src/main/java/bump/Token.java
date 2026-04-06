package bump;

public class Token {
    private TokenType type;
    private String text;
    private int line;
    private int column;
    private int sourceLength;

    public Token(TokenType type, String text, int line, int column, int sourceLength) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
        this.sourceLength = sourceLength;
    }

    public TokenType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public int getSourceLength() {
        return sourceLength;
    }

    @Override
    public String toString(){
        return type + " \"" + text + "\" at line " + line + ", column " + column;
    }
}
