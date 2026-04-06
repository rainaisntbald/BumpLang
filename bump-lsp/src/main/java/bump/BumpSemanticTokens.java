package bump;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

final class BumpSemanticTokens {
    private static final List<String> TOKEN_TYPES = List.of(
            "namespace",
            "type",
            "class",
            "enum",
            "interface",
            "struct",
            "typeParameter",
            "parameter",
            "variable",
            "property",
            "enumMember",
            "event",
            "function",
            "method",
            "macro",
            "keyword",
            "modifier",
            "comment",
            "string",
            "number",
            "regexp",
            "operator",
            "decorator"
    );

    private static final List<String> TOKEN_MODIFIERS = List.of(
            "declaration",
            "readonly",
            "static"
    );

    private static final int TYPE_CLASS = indexOfType("class");
    private static final int TYPE_FUNCTION = indexOfType("function");
    private static final int TYPE_METHOD = indexOfType("method");
    private static final int TYPE_VARIABLE = indexOfType("variable");
    private static final int TYPE_PROPERTY = indexOfType("property");
    private static final int TYPE_KEYWORD = indexOfType("keyword");
    private static final int TYPE_COMMENT = indexOfType("comment");
    private static final int TYPE_STRING = indexOfType("string");
    private static final int TYPE_NUMBER = indexOfType("number");
    private static final int TYPE_OPERATOR = indexOfType("operator");
    private static final int TYPE_MACRO = indexOfType("macro");

    private static final int MOD_DECLARATION_BIT = 1 << indexOfModifier("declaration");

    private static final EnumSet<TokenType> KEYWORD_TYPES = EnumSet.of(
            TokenType.WHILE,
            TokenType.FOR,
            TokenType.IF,
            TokenType.ELSE,
            TokenType.TRY,
            TokenType.CATCH,
            TokenType.FINALLY,
            TokenType.DEFAULT,
            TokenType.SWITCH,
            TokenType.CASE,
            TokenType.BREAK,
            TokenType.CONTINUE,
            TokenType.FUN,
            TokenType.PUBLIC,
            TokenType.PRIVATE,
            TokenType.RETURN,
            TokenType.CLASS,
            TokenType.EXTENDS,
            TokenType.IMPLEMENTS,
            TokenType.ABSTRACT,
            TokenType.ENUM,
            TokenType.EXTEND,
            TokenType.SUPER,
            TokenType.BOOLEAN,
            TokenType.NULL,
            TokenType.VOID
    );

    private static final EnumSet<TokenType> OPERATOR_TYPES = EnumSet.of(
            TokenType.AND,
            TokenType.OR,
            TokenType.PLUSPLUS,
            TokenType.MINUSMINUS,
            TokenType.PLUSASSIGN,
            TokenType.MINUSASSIGN,
            TokenType.MULASSIGN,
            TokenType.DIVASSIGN,
            TokenType.EQ,
            TokenType.NEQ,
            TokenType.GTEQ,
            TokenType.GT,
            TokenType.LTEQ,
            TokenType.LT,
            TokenType.NOT,
            TokenType.FATARROW,
            TokenType.ASSIGN,
            TokenType.PLUS,
            TokenType.MINUS,
            TokenType.MUL,
            TokenType.MOD,
            TokenType.DIV
    );

    private static final Lexer HIGHLIGHTER_LEXER = new Lexer();

    private BumpSemanticTokens() {
    }

    static SemanticTokensLegend legend() {
        return new SemanticTokensLegend(TOKEN_TYPES, TOKEN_MODIFIERS);
    }

    static SemanticTokens forSource(String source) {
        return forSourceInRange(source, null);
    }

    static SemanticTokens forSourceInRange(String source, Range range) {
        List<Token> tokens;
        try {
            tokens = HIGHLIGHTER_LEXER.tokenizeForHighlighting(source);
        } catch (RuntimeException error) {
            return new SemanticTokens(List.of());
        }
        List<Integer> data = new ArrayList<>(tokens.size() * 5);

        int prevLine = 0;
        int prevColumn = 0;
        boolean hasPrevious = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (!isInRequestedRange(token, range)) {
                continue;
            }
            int tokenType = classifyTokenType(tokens, i);
            if (tokenType < 0) {
                continue;
            }
            int modifiers = classifyModifiers(tokens, i, tokenType);

            int line = Math.max(0, token.getLine() - 1);
            int column = Math.max(0, token.getColumn() - 1);
            int length = Math.max(1, token.getSourceLength());

            int deltaLine = hasPrevious ? line - prevLine : line;
            int deltaColumn = (!hasPrevious || deltaLine != 0) ? column : column - prevColumn;
            data.add(deltaLine);
            data.add(deltaColumn);
            data.add(length);
            data.add(tokenType);
            data.add(modifiers);

            prevLine = line;
            prevColumn = column;
            hasPrevious = true;
        }

        return new SemanticTokens(data);
    }

    private static boolean isInRequestedRange(Token token, Range range) {
        if (range == null) {
            return true;
        }
        int line = Math.max(0, token.getLine() - 1);
        int startChar = Math.max(0, token.getColumn() - 1);
        int endChar = Math.max(startChar + 1, startChar + Math.max(1, token.getSourceLength()));
        int startLine = range.getStart().getLine();
        int endLine = range.getEnd().getLine();
        if (line < startLine || line > endLine) {
            return false;
        }
        if (line == startLine && endChar <= range.getStart().getCharacter()) {
            return false;
        }
        if (line == endLine && startChar >= range.getEnd().getCharacter()) {
            return false;
        }
        return true;
    }

    private static int classifyTokenType(List<Token> tokens, int index) {
        Token token = tokens.get(index);
        TokenType type = token.getType();
        if (type == TokenType.JAVABLOCK) {
            return TYPE_MACRO;
        }
        if (type == TokenType.STRING) {
            return TYPE_STRING;
        }
        if (type == TokenType.INTEGER || type == TokenType.FLOAT) {
            return TYPE_NUMBER;
        }
        if (type == TokenType.COMMENT) {
            return TYPE_COMMENT;
        }
        if (KEYWORD_TYPES.contains(type)) {
            return TYPE_KEYWORD;
        }
        if (OPERATOR_TYPES.contains(type)) {
            return TYPE_OPERATOR;
        }
        if (type != TokenType.ID) {
            return -1;
        }

        Token previous = previousToken(tokens, index);
        Token next = nextToken(tokens, index);
        TokenType previousType = previous != null ? previous.getType() : null;
        TokenType nextType = next != null ? next.getType() : null;

        if (previousType == TokenType.CLASS
                || previousType == TokenType.EXTENDS
                || previousType == TokenType.IMPLEMENTS
                || previousType == TokenType.ENUM
                || previousType == TokenType.EXTEND) {
            return TYPE_CLASS;
        }
        if (previousType == TokenType.DOT) {
            return nextType == TokenType.LPAREN ? TYPE_METHOD : TYPE_PROPERTY;
        }
        if (previousType == TokenType.FUN) {
            return TYPE_FUNCTION;
        }
        if (nextType == TokenType.LPAREN) {
            return TYPE_FUNCTION;
        }
        if (startsUppercase(token.getText())) {
            return TYPE_CLASS;
        }
        return TYPE_VARIABLE;
    }

    private static int classifyModifiers(List<Token> tokens, int index, int tokenType) {
        if (tokenType != TYPE_CLASS && tokenType != TYPE_FUNCTION) {
            return 0;
        }
        Token previous = previousToken(tokens, index);
        if (previous == null) {
            return 0;
        }
        if (previous.getType() == TokenType.CLASS
                || previous.getType() == TokenType.ENUM
                || previous.getType() == TokenType.FUN) {
            return MOD_DECLARATION_BIT;
        }
        return 0;
    }

    private static Token previousToken(List<Token> tokens, int index) {
        return index > 0 ? tokens.get(index - 1) : null;
    }

    private static Token nextToken(List<Token> tokens, int index) {
        return index + 1 < tokens.size() ? tokens.get(index + 1) : null;
    }

    private static boolean startsUppercase(String text) {
        return text != null && !text.isEmpty() && Character.isUpperCase(text.charAt(0));
    }

    private static int indexOfType(String tokenType) {
        return TOKEN_TYPES.indexOf(tokenType);
    }

    private static int indexOfModifier(String modifier) {
        return TOKEN_MODIFIERS.indexOf(modifier);
    }
}
