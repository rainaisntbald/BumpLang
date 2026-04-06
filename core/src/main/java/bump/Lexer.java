package bump;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Lexer {
    private static final Pattern tokenPattern;

    static {
        StringBuilder combinedRegex = new StringBuilder();
        for (TokenType tokenType : TokenType.values()) {
            if (!combinedRegex.isEmpty()) combinedRegex.append("|");
            combinedRegex.append(String.format("(?<%s>%s)", tokenType.name(), tokenType.getRegex()));
        }
        tokenPattern = Pattern.compile(combinedRegex.toString());
    }

    public List<Token> tokenize(String code) {
        return tokenize(code, false);
    }

    public List<Token> tokenizeForHighlighting(String code) {
        return tokenize(code, true);
    }

    private List<Token> tokenize(String code, boolean includeComments) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        int line = 1;
        int column = 1;

        while (index < code.length()) {
            StringLiteralMatch stringLiteral = tryMatchStringLiteral(code, index, line, column);
            if (stringLiteral != null) {
                tokens.add(stringLiteral.token());
                index = stringLiteral.nextIndex();
                line = stringLiteral.nextLine();
                column = stringLiteral.nextColumn();
                continue;
            }

            JavaBlockMatch javaBlock = tryMatchJavaBlock(code, index, line, column);
            if (javaBlock != null) {
                tokens.add(javaBlock.token());
                index = javaBlock.nextIndex();
                line = javaBlock.nextLine();
                column = javaBlock.nextColumn();
                continue;
            }

            Matcher matcher = tokenPattern.matcher(code);
            matcher.region(index, code.length());
            if (!matcher.lookingAt()) {
                throw BumpException.syntax(line, "Unexpected token '" + code.charAt(index) + "'.");
            }

            TokenType matchedType = null;
            String match = null;
            for (TokenType tokenType : TokenType.values()) {
                String group = matcher.group(tokenType.name());
                if (group == null) continue;
                matchedType = tokenType;
                match = group;
                break;
            }

            if (matchedType == null || match == null) {
                throw BumpException.internal("Lexer failed to classify token.");
            }

            int startLine = line;
            int startColumn = column;
            index = matcher.end();
            int[] updatedPosition = advancePosition(match, line, column);
            line = updatedPosition[0];
            column = updatedPosition[1];

            if (matchedType == TokenType.WHITESPACE) {
                continue;
            }
            if (matchedType == TokenType.COMMENT && !includeComments) {
                continue;
            }

            tokens.add(new Token(matchedType, match, startLine, startColumn, Math.max(1, matcher.end() - matcher.start())));
        }

        return tokens;
    }

    private StringLiteralMatch tryMatchStringLiteral(String code, int index, int line, int column) {
        if (index >= code.length() || code.charAt(index) != '"') {
            return null;
        }

        boolean multiline = index + 2 < code.length()
                && code.charAt(index + 1) == '"'
                && code.charAt(index + 2) == '"';
        int delimiterLength = multiline ? 3 : 1;
        int cursor = index + delimiterLength;
        StringBuilder decoded = new StringBuilder();

        while (cursor < code.length()) {
            if (multiline) {
                if (cursor + 2 < code.length()
                        && code.charAt(cursor) == '"'
                        && code.charAt(cursor + 1) == '"'
                        && code.charAt(cursor + 2) == '"') {
                    String consumed = code.substring(index, cursor + 3);
                    int[] updatedPosition = advancePosition(consumed, line, column);
                    Token token = new Token(TokenType.STRING, decoded.toString(), line, column, Math.max(1, consumed.length()));
                    return new StringLiteralMatch(token, cursor + 3, updatedPosition[0], updatedPosition[1]);
                }
            } else if (code.charAt(cursor) == '"') {
                String consumed = code.substring(index, cursor + 1);
                int[] updatedPosition = advancePosition(consumed, line, column);
                Token token = new Token(TokenType.STRING, decoded.toString(), line, column, Math.max(1, consumed.length()));
                return new StringLiteralMatch(token, cursor + 1, updatedPosition[0], updatedPosition[1]);
            }

            char ch = code.charAt(cursor);
            if (!multiline && (ch == '\n' || ch == '\r')) {
                throw BumpException.syntax(line, "Unterminated string literal.");
            }

            if (ch == '\\') {
                if (cursor + 1 >= code.length()) {
                    throw BumpException.syntax(line, "Unterminated string literal.");
                }
                char escape = code.charAt(cursor + 1);
                switch (escape) {
                    case '"' -> {
                        decoded.append('"');
                        cursor += 2;
                    }
                    case '\'' -> {
                        decoded.append('\'');
                        cursor += 2;
                    }
                    case '\\' -> {
                        decoded.append('\\');
                        cursor += 2;
                    }
                    case 'n' -> {
                        decoded.append('\n');
                        cursor += 2;
                    }
                    case 'r' -> {
                        decoded.append('\r');
                        cursor += 2;
                    }
                    case 't' -> {
                        decoded.append('\t');
                        cursor += 2;
                    }
                    case 'b' -> {
                        decoded.append('\b');
                        cursor += 2;
                    }
                    case 'f' -> {
                        decoded.append('\f');
                        cursor += 2;
                    }
                    case 'u' -> {
                        if (cursor + 5 >= code.length()) {
                            throw BumpException.syntax(line, "Invalid unicode escape sequence in string literal.");
                        }
                        String hex = code.substring(cursor + 2, cursor + 6);
                        try {
                            int codePoint = Integer.parseInt(hex, 16);
                            decoded.append((char) codePoint);
                        } catch (NumberFormatException error) {
                            throw BumpException.syntax(line, "Invalid unicode escape sequence in string literal.");
                        }
                        cursor += 6;
                    }
                    default -> throw BumpException.syntax(line, "Invalid escape sequence '\\" + escape + "' in string literal.");
                }
                continue;
            }

            decoded.append(ch);
            cursor++;
        }

        throw BumpException.syntax(line, "Unterminated string literal.");
    }

    private JavaBlockMatch tryMatchJavaBlock(String code, int index, int line, int column) {
        if (!code.startsWith("unsafe_java", index)) {
            return null;
        }
        int afterKeyword = index + 11;
        if (afterKeyword < code.length() && isIdentifierChar(code.charAt(afterKeyword))) {
            return null;
        }

        int cursor = afterKeyword;
        int currentLine = line;
        int currentColumn = column + 11;

        while (cursor < code.length()) {
            char ch = code.charAt(cursor);
            if (ch == ' ' || ch == '\t' || ch == '\r') {
                cursor++;
                currentColumn++;
                continue;
            }
            if (ch == '\n') {
                cursor++;
                currentLine++;
                currentColumn = 1;
                continue;
            }
            break;
        }

        if (cursor >= code.length() || code.charAt(cursor) != '{') {
            return null;
        }

        int bodyStart = cursor + 1;
        int scan = bodyStart;
        int depth = 1;
        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        while (scan < code.length()) {
            char ch = code.charAt(scan);
            char next = scan + 1 < code.length() ? code.charAt(scan + 1) : '\0';

            if (inLineComment) {
                if (ch == '\n') {
                    inLineComment = false;
                }
                scan++;
                continue;
            }

            if (inBlockComment) {
                if (ch == '*' && next == '/') {
                    inBlockComment = false;
                    scan += 2;
                    continue;
                }
                scan++;
                continue;
            }

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                scan++;
                continue;
            }

            if (inChar) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '\'') {
                    inChar = false;
                }
                scan++;
                continue;
            }

            if (ch == '/' && next == '/') {
                inLineComment = true;
                scan += 2;
                continue;
            }
            if (ch == '/' && next == '*') {
                inBlockComment = true;
                scan += 2;
                continue;
            }
            if (ch == '"') {
                inString = true;
                scan++;
                continue;
            }
            if (ch == '\'') {
                inChar = true;
                scan++;
                continue;
            }
            if (ch == '{') {
                depth++;
                scan++;
                continue;
            }
            if (ch == '}') {
                depth--;
                if (depth == 0) {
                    String body = code.substring(bodyStart, scan);
                    String consumed = code.substring(index, scan + 1);
                    int[] updatedPosition = advancePosition(consumed, line, column);
                    Token token = new Token(TokenType.JAVABLOCK, body, line, column, Math.max(1, consumed.length()));
                    return new JavaBlockMatch(token, scan + 1, updatedPosition[0], updatedPosition[1]);
                }
                scan++;
                continue;
            }
            scan++;
        }

        throw BumpException.syntax(line, "Unterminated java block.");
    }

    private int[] advancePosition(String text, int line, int column) {
        int currentLine = line;
        int currentColumn = column;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                currentLine++;
                currentColumn = 1;
            } else {
                currentColumn++;
            }
        }
        return new int[]{currentLine, currentColumn};
    }

    private boolean isIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private record StringLiteralMatch(Token token, int nextIndex, int nextLine, int nextColumn) {}
    private record JavaBlockMatch(Token token, int nextIndex, int nextLine, int nextColumn) {}
}
