package bump;

public enum TokenType {
    JAVABLOCK("\\b\\B"),
    STRING("\"[^\"]*\""),
    WHITESPACE("[ \\t\\r\\n]+"),
    COMMENT("//[^\\n\\r]*"),
    BOOLEAN("true\\b|false\\b"),
    NULL("null\\b"),
    VOID("void\\b"),
    LBRACKET("\\["),
    RBRACKET("\\]"),
    AND("&&"),
    OR("\\|\\|"),
    PLUSPLUS("\\+\\+"),
    MINUSMINUS("--"),
    PLUSASSIGN("\\+="),
    MINUSASSIGN("-="),
    MULASSIGN("\\*="),
    DIVASSIGN("/="),
    EQ("=="),
    NEQ("!="),
    GTEQ(">="),
    GT(">"),
    LTEQ("<="),
    LT("<"),
    NOT("!"),
    SUPER("super\\b"),
    FATARROW("=>"),
    ASSIGN("="),
    LPAREN("\\("),
    RPAREN("\\)"),
    PLUS("\\+"),
    MINUS("\\-"),
    MUL("\\*"),
    MOD("\\%"),
    DIV("\\/"),
    FLOAT("\\d+\\.\\d+"),
    INTEGER("\\d+"),
    WHILE("while\\b"),
    FOR("for\\b"),
    IF("if\\b"),
    ELSE("else\\b"),
    TRY("try\\b"),
    CATCH("catch\\b"),
    FINALLY("finally\\b"),
    DEFAULT("default\\b"),
    SWITCH("switch\\b"),
    CASE("case\\b"),
    BREAK("break\\b"),
    CONTINUE("continue\\b"),
    FUN("fun\\b"),
    PUBLIC("public\\b"),
    PRIVATE("private\\b"),
    RETURN("return\\b"),
    CLASS("class\\b"),
    EXTENDS("extends\\b"),
    IMPLEMENTS("implements\\b"),
    ABSTRACT("abstract\\b"),
    ENUM("enum\\b"),
    EXTEND("extend\\b"),
    LBRACE("\\{"),
    RBRACE("\\}"),
    SEMICOLON(";"),
    COLON(":"),
    COMMA(","),
    DOT("\\."),
    ID("[a-zA-Z_][a-zA-Z0-9_]*"), // Keep ID at bottom, things go fucky if you don't
    ;

    private final String regex;

    TokenType(String regex) {
        this.regex = regex;
    }

    public String getRegex() {
        return regex;
    }
}
