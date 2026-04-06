package bump;

import nativeClasses.NullInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BumpException extends RuntimeException {
    public record SourceLocation(String file, Integer line) {}

    private static List<String> sourceLines = List.of();
    private static List<SourceLocation> sourceLocations = List.of();
    private final Integer line;
    private final Integer column;
    private final Integer length;
    private final String category;
    private final String detail;
    private final String context;

    private BumpException(String category, Integer line, Integer column, Integer length, String message, String context) {
        super(formatMessage(category, line, column, length, message, context));
        this.category = category;
        this.line = line;
        this.column = column;
        this.length = length;
        this.detail = message;
        this.context = context;
    }

    public static BumpException syntax(Token token, String message) {
        return new BumpException(
                "Syntax error",
                token != null ? token.getLine() : null,
                token != null ? token.getColumn() : null,
                token != null ? token.getSourceLength() : null,
                message,
                token != null ? "'" + token.getText() + "'" : null
        );
    }

    public static BumpException syntax(int line, String message) {
        return new BumpException("Syntax error", line, null, null, message, null);
    }

    public static BumpException semantic(String message) {
        return new BumpException("Semantic error", null, null, null, message, null);
    }

    public static BumpException semantic(Token token, String message) {
        return new BumpException(
                "Semantic error",
                token != null ? token.getLine() : null,
                token != null ? token.getColumn() : null,
                token != null ? token.getSourceLength() : null,
                message,
                token != null ? "'" + token.getText() + "'" : null
        );
    }

    public static BumpException semantic(int line, String message) {
        return new BumpException("Semantic error", line, null, null, message, null);
    }

    public static BumpException runtime(String message) {
        return new BumpException("Runtime error", null, null, null, message, null);
    }

    public static BumpException runtime(Token token, String message) {
        return new BumpException(
                "Runtime error",
                token != null ? token.getLine() : null,
                token != null ? token.getColumn() : null,
                token != null ? token.getSourceLength() : null,
                message,
                token != null ? "'" + token.getText() + "'" : null
        );
    }

    public static BumpException runtime(int line, String message) {
        return new BumpException("Runtime error", line, null, null, message, null);
    }

    public static BumpException internal(String message) {
        return new BumpException("Internal error", null, null, null, message, null);
    }

    public static BumpException internal(Throwable error) {
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = error.getClass().getSimpleName();
        } else {
            detail = error.getClass().getSimpleName() + ": " + detail;
        }
        return internal(detail);
    }

    public Integer getLine() {
        SourceLocation location = resolveSourceLocation(line);
        return location != null && location.line() != null ? location.line() : line;
    }

    public Integer getColumn() {
        return column;
    }

    public Integer getLength() {
        return length;
    }

    public String getFile() {
        SourceLocation location = resolveSourceLocation(line);
        return location != null ? location.file() : null;
    }

    public static String sourceFileForLine(Integer line) {
        SourceLocation location = resolveSourceLocation(line);
        return location != null ? location.file() : null;
    }

    public static SourceLocation sourceLocationForLine(Integer line) {
        return resolveSourceLocation(line);
    }

    public String getCategory() {
        return category;
    }

    public String getDetail() {
        return detail;
    }

    public String getContext() {
        return context;
    }

    public BumpException withFallbackLocation(int fallbackLine, int fallbackColumn, int fallbackLength, String fallbackContext) {
        Integer resolvedLine = line != null ? line : (fallbackLine > 0 ? fallbackLine : null);
        Integer resolvedColumn = column != null ? column : (fallbackColumn > 0 ? fallbackColumn : null);
        Integer resolvedLength = length != null ? length : (fallbackLength > 0 ? fallbackLength : null);
        String resolvedContext = context != null ? context : normalizeContext(fallbackContext);
        if ((line == null && resolvedLine != null)
                || (column == null && resolvedColumn != null)
                || (length == null && resolvedLength != null)
                || (context == null && resolvedContext != null)) {
            return new BumpException(category, resolvedLine, resolvedColumn, resolvedLength, detail, resolvedContext);
        }
        return this;
    }

    public static void setSource(String code) {
        sourceLines = List.of(code.split("\n", -1));
        sourceLocations = List.of();
    }

    public static void setSource(String code, List<SourceLocation> locations) {
        sourceLines = List.of(code.split("\n", -1));
        int lineCount = sourceLines.size();
        List<SourceLocation> normalized = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            SourceLocation location = i < locations.size() ? locations.get(i) : null;
            normalized.add(location);
        }
        sourceLocations = Collections.unmodifiableList(normalized);
    }

    public static String formatLocation(Integer line, Integer column) {
        if (line == null) {
            return null;
        }
        SourceLocation location = resolveSourceLocation(line);
        int displayLine = location != null && location.line() != null ? location.line() : line;
        if (location != null && location.file() != null) {
            if (column != null) {
                return location.file() + ":" + displayLine + ":" + column;
            }
            return location.file() + ":" + displayLine;
        }
        if (column != null) {
            return "line " + displayLine + ", column " + column;
        }
        return "line " + displayLine;
    }

    public static String describeValue(Object value) {
        if (value == null || value instanceof NullInstance) {
            return "null";
        }
        return value.toString();
    }

    public static String describeType(Object value) {
        if (value == null || value instanceof NullInstance) {
            return "Null";
        }
        if (value instanceof BumpInstance instance) {
            return instance.getRuntimeClass().name;
        }
        return value.getClass().getSimpleName();
    }

    private static String formatMessage(String category, Integer line, Integer column, Integer length, String message, String context) {
        StringBuilder builder = new StringBuilder(category);
        String location = formatLocation(line, column);
        if (location != null) {
            builder.append(" at ").append(location);
        }
        builder.append(": ").append(message);

        if (line != null && line >= 1 && line <= sourceLines.size()) {
            String sourceLine = sourceLines.get(line - 1);
            builder.append("\n  ").append(sourceLine);
            if (column != null && column >= 1) {
                int available = Math.max(1, sourceLine.length() - column + 1);
                int caretLength = Math.max(1, Math.min(length != null ? length : 1, available));
                builder.append("\n  ")
                        .append(" ".repeat(Math.max(0, column - 1)))
                        .append("^".repeat(caretLength));
            }
            return builder.toString();
        }

        if (context != null) {
            builder.append("\n  Near ").append(context).append(".");
        }
        return builder.toString();
    }

    private static String normalizeContext(String context) {
        if (context == null) {
            return null;
        }
        String summary = context.replaceAll("\\s+", " ").trim();
        if (summary.isEmpty()) {
            return null;
        }
        if (summary.length() > 80) {
            return summary.substring(0, 77) + "...";
        }
        return summary;
    }

    private static SourceLocation resolveSourceLocation(Integer line) {
        if (line == null || line < 1 || line > sourceLocations.size()) {
            return null;
        }
        return sourceLocations.get(line - 1);
    }
}
