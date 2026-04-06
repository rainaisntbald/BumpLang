package bump;

final class ParserLookahead {
    private final Parser parser;

    ParserLookahead(Parser parser) {
        this.parser = parser;
    }

    boolean looksLikeTypedDeclaration() {
        int nextOffset = scanTypeName(0);
        if (nextOffset < 0) {
            return false;
        }
        Token name = parser.peek(nextOffset);
        return name != null && name.getType() == TokenType.ID;
    }

    boolean looksLikeEnhancedForHeader() {
        int afterType = scanTypeName(0);
        if (afterType < 0) {
            return false;
        }
        Token variable = parser.peek(afterType);
        Token separator = parser.peek(afterType + 1);
        return variable != null
                && separator != null
                && variable.getType() == TokenType.ID
                && separator.getType() == TokenType.COLON;
    }

    boolean looksLikeTypeArguments() {
        return looksLikeTypeArgumentList(true);
    }

    boolean looksLikeCallTypeArguments() {
        return looksLikeTypeArgumentList(false);
    }

    private boolean looksLikeTypeArgumentList(boolean allowDotAfterGt) {
        if (!parser.check(TokenType.LT)) {
            return false;
        }
        int current = 1;
        while (true) {
            int typeEnd = scanTypeName(current);
            if (typeEnd < 0) {
                return false;
            }
            current = typeEnd;
            Token separator = parser.peek(current);
            if (separator == null) {
                return false;
            }
            if (separator.getType() == TokenType.COMMA) {
                current++;
                continue;
            }
            if (separator.getType() == TokenType.GT) {
                Token afterGT = parser.peek(current + 1);
                if (afterGT == null) {
                    return false;
                }
                if (afterGT.getType() == TokenType.LPAREN) {
                    return true;
                }
                return allowDotAfterGt && afterGT.getType() == TokenType.DOT;
            }
            return false;
        }
    }

    boolean looksLikeLambdaLiteral() {
        if (!parser.check(TokenType.LPAREN)) {
            return false;
        }
        int current = 1;
        Token first = parser.peek(current);
        if (first == null) {
            return false;
        }
        if (first.getType() == TokenType.RPAREN) {
            Token afterParen = parser.peek(current + 1);
            return afterParen != null && afterParen.getType() == TokenType.FATARROW;
        }
        while (true) {
            int afterType = scanTypeName(current);
            if (afterType < 0) {
                return false;
            }
            Token parameterName = parser.peek(afterType);
            if (parameterName == null || parameterName.getType() != TokenType.ID) {
                return false;
            }
            current = afterType + 1;
            Token separator = parser.peek(current);
            if (separator == null) {
                return false;
            }
            if (separator.getType() == TokenType.COMMA) {
                current++;
                continue;
            }
            if (separator.getType() == TokenType.RPAREN) {
                Token afterParen = parser.peek(current + 1);
                return afterParen != null && afterParen.getType() == TokenType.FATARROW;
            }
            return false;
        }
    }

    private int scanTypeName(int offset) {
        Token first = parser.peek(offset);
        if (first == null || first.getType() != TokenType.ID) {
            return -1;
        }
        int current = offset + 1;
        Token next = parser.peek(current);
        if (next == null || next.getType() != TokenType.LT) {
            return current;
        }
        current++;
        while (true) {
            int nestedEnd = scanTypeName(current);
            if (nestedEnd < 0) {
                return -1;
            }
            current = nestedEnd;
            Token separator = parser.peek(current);
            if (separator == null) {
                return -1;
            }
            if (separator.getType() == TokenType.COMMA) {
                current++;
                continue;
            }
            if (separator.getType() == TokenType.GT) {
                return current + 1;
            }
            return -1;
        }
    }
}
