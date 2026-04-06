package bump;

import java.util.List;

final class ParserCursor {
    private final List<Token> tokens;
    private int position = 0;

    ParserCursor(List<Token> tokens) {
        this.tokens = tokens;
    }

    Token current() {
        return peek(0);
    }

    Token previous() {
        return tokens.get(position - 1);
    }

    Token peek(int offset) {
        int index = position + offset;
        return index < tokens.size() ? tokens.get(index) : null;
    }

    boolean isAtEnd() {
        return current() == null;
    }

    Token advance() {
        return tokens.get(position++);
    }

    boolean match(TokenType type) {
        if (current() != null && current().getType() == type) {
            advance();
            return true;
        }
        return false;
    }

    Token expect(TokenType type) {
        Token token = current();
        if (token == null) {
            throw BumpException.syntax((Token) null, "Expected " + type + " but reached end of file.");
        }
        if (token.getType() != type) {
            throw BumpException.syntax(token, "Expected " + type + " but found " + token.getType() + ".");
        }
        return advance();
    }
}
