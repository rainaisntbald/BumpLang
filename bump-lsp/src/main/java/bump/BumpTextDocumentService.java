package bump;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class BumpTextDocumentService implements TextDocumentService {
    private record DotCompletionContext(String receiver, String prefix) {}

    private final ProgramRunner analyzer = new ProgramRunner();
    private final Lexer lexer = new Lexer();
    private final boolean emitUnsafeJavaWarnings;
    private final Map<String, String> openDocuments = new HashMap<>();
    private LanguageClient client;

    BumpTextDocumentService(boolean emitUnsafeJavaWarnings) {
        this.emitUnsafeJavaWarnings = emitUnsafeJavaWarnings;
    }

    void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem document = params.getTextDocument();
        openDocuments.put(document.getUri(), document.getText());
        publishDiagnostics(document.getUri(), document.getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (changes.isEmpty()) {
            return;
        }
        String text = changes.get(changes.size() - 1).getText();
        openDocuments.put(uri, text);
        publishDiagnostics(uri, text);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        openDocuments.remove(uri);
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        String text = openDocuments.get(uri);
        if (text != null) {
            publishDiagnostics(uri, text);
        }
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        TextDocumentIdentifier document = params.getTextDocument();
        String source = sourceForUri(document.getUri());
        if (source == null) {
            return CompletableFuture.completedFuture(new SemanticTokens(List.of()));
        }
        return CompletableFuture.completedFuture(BumpSemanticTokens.forSource(source));
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensRange(SemanticTokensRangeParams params) {
        TextDocumentIdentifier document = params.getTextDocument();
        String source = sourceForUri(document.getUri());
        if (source == null) {
            return CompletableFuture.completedFuture(new SemanticTokens(List.of()));
        }
        return CompletableFuture.completedFuture(BumpSemanticTokens.forSourceInRange(source, params.getRange()));
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        TextDocumentIdentifier document = position.getTextDocument();
        String source = sourceForUri(document.getUri());
        if (source == null) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
        Path baseDirectory = baseDirectoryForUri(document.getUri());
        DotCompletionContext dotContext = extractDotCompletionContext(source, position.getPosition());
        String completionSource = dotContext == null
                ? source
                : sourceWithCompletionSentinel(source, position.getPosition());
        int sourceLine = position.getPosition().getLine() + 1;
        ProgramRunner.CompletionSnapshot snapshot = analyzer.completionSnapshot(completionSource, baseDirectory, sourceLine);
        if (snapshot.resolver() == null || snapshot.symbols().isEmpty()) {
            return CompletableFuture.completedFuture(Either.forLeft(List.of()));
        }
        List<CompletionItem> out = new ArrayList<>();
        if (dotContext != null) {
            populateMemberCompletions(dotContext, snapshot, out);
        } else {
            populateTopLevelCompletions(source, position.getPosition(), snapshot.symbols(), out);
        }
        return CompletableFuture.completedFuture(Either.forLeft(out));
    }

    private String sourceForUri(String uri) {
        String fromOpenDocs = openDocuments.get(uri);
        if (fromOpenDocs != null) {
            return fromOpenDocs;
        }
        try {
            URI parsed = URI.create(uri);
            if (!"file".equals(parsed.getScheme())) {
                return null;
            }
            Path path = Path.of(parsed);
            if (!Files.exists(path)) {
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void publishDiagnostics(String uri, String source) {
        if (client == null) {
            return;
        }
        Path baseDirectory = baseDirectoryForUri(uri);

        List<BumpException> errors = analyzer.collectDiagnostics(source, baseDirectory);
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (BumpException error : errors) {
            diagnostics.add(toDiagnostic(error, source));
        }
        if (emitUnsafeJavaWarnings) {
            appendUnsafeJavaWarnings(diagnostics, source);
        }
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    private Path baseDirectoryForUri(String uri) {
        Path baseDirectory = Path.of(".").toAbsolutePath().normalize();
        try {
            URI parsed = URI.create(uri);
            if ("file".equals(parsed.getScheme())) {
                Path path = Path.of(parsed);
                Path parent = path.getParent();
                if (parent != null) {
                    baseDirectory = parent;
                }
            }
        } catch (Exception ignored) {
        }
        return baseDirectory;
    }

    private DotCompletionContext extractDotCompletionContext(String source, Position position) {
        int offset = offsetForPosition(source, position);
        if (offset <= 0) {
            return null;
        }
        String left = source.substring(0, offset);
        int i = left.length() - 1;
        while (i >= 0 && isIdentifierPart(left.charAt(i))) {
            i--;
        }
        String prefix = left.substring(i + 1);
        if (i < 0 || left.charAt(i) != '.') {
            return null;
        }
        i--;
        while (i >= 0 && Character.isWhitespace(left.charAt(i))) {
            i--;
        }
        int receiverEnd = i;
        while (i >= 0 && isIdentifierPart(left.charAt(i))) {
            i--;
        }
        if (receiverEnd < i + 1) {
            return null;
        }
        String receiver = left.substring(i + 1, receiverEnd + 1);
        if (!isIdentifierStart(receiver.charAt(0))) {
            return null;
        }
        return new DotCompletionContext(receiver, prefix);
    }

    private void populateMemberCompletions(
            DotCompletionContext context,
            ProgramRunner.CompletionSnapshot snapshot,
            List<CompletionItem> out
    ) {
        SymbolInfo symbol = snapshot.symbols().get(context.receiver());
        if (symbol == null || symbol.semanticType() == null) {
            return;
        }
        Resolver resolver = snapshot.resolver();
        SemanticType receiverType = symbol.semanticType();
        SymbolInfo classSymbol = snapshot.symbols().get(receiverType.name());
        ClassInfo classInfo = classSymbol == null ? null : classSymbol.classInfo();
        if (classInfo == null) {
            return;
        }

        Set<String> fieldNames = new LinkedHashSet<>();
        collectFieldNames(classInfo, fieldNames);
        for (String fieldName : fieldNames) {
            if (!startsWithPrefix(fieldName, context.prefix())) {
                continue;
            }
            CompletionItem field = new CompletionItem(fieldName);
            field.setKind(CompletionItemKind.Field);
            SemanticType fieldType = resolver.bindFieldType(classInfo, receiverType, fieldName);
            if (fieldType != null) {
                field.setDetail(fieldType.displayName());
            }
            out.add(field);
        }

        Set<String> methodNames = new LinkedHashSet<>();
        collectMethodNames(classInfo, methodNames);
        for (String methodName : methodNames) {
            if (!startsWithPrefix(methodName, context.prefix())) {
                continue;
            }
            CompletionItem method = new CompletionItem(methodName);
            method.setKind(CompletionItemKind.Method);
            List<SemanticType> overloads = resolver.bindMethodOverloads(classInfo, receiverType, methodName);
            if (!overloads.isEmpty()) {
                method.setDetail(formatOverload(overloads.get(0)));
            }
            out.add(method);
        }
    }

    private void populateTopLevelCompletions(
            String source,
            Position position,
            Map<String, SymbolInfo> symbols,
            List<CompletionItem> out
    ) {
        String prefix = extractWordPrefix(source, position);
        for (Map.Entry<String, SymbolInfo> entry : symbols.entrySet()) {
            String name = entry.getKey();
            if (!startsWithPrefix(name, prefix)) {
                continue;
            }
            CompletionItem item = new CompletionItem(name);
            item.setKind(symbolKindToCompletionKind(entry.getValue().kind()));
            SemanticType semanticType = entry.getValue().semanticType();
            if (semanticType != null) {
                item.setDetail(semanticType.displayName());
            }
            out.add(item);
        }
    }

    private void collectFieldNames(ClassInfo classInfo, Set<String> out) {
        if (classInfo == null) {
            return;
        }
        for (String fieldName : classInfo.fields.keySet()) {
            if (classInfo.privateFields.getOrDefault(fieldName, false)) {
                continue;
            }
            out.add(fieldName);
        }
        collectFieldNames(classInfo.superclass, out);
    }

    private void collectMethodNames(ClassInfo classInfo, Set<String> out) {
        if (classInfo == null) {
            return;
        }
        for (String methodName : classInfo.methods.keySet()) {
            if (classInfo.privateMethods.getOrDefault(methodName, false)) {
                continue;
            }
            out.add(methodName);
        }
        collectMethodNames(classInfo.superclass, out);
        for (ClassInfo iface : classInfo.interfaces) {
            collectMethodNames(iface, out);
        }
    }

    private String formatOverload(SemanticType overload) {
        List<String> parameterTypes = overload.parameterTypes().stream()
                .map(SemanticType::displayName)
                .toList();
        String returnType = overload.returnType() == null ? "Null" : overload.returnType().displayName();
        return "(" + String.join(", ", parameterTypes) + ") -> " + returnType;
    }

    private String extractWordPrefix(String source, Position position) {
        int offset = offsetForPosition(source, position);
        if (offset <= 0) {
            return "";
        }
        int i = offset - 1;
        while (i >= 0 && isIdentifierPart(source.charAt(i))) {
            i--;
        }
        return source.substring(i + 1, offset);
    }

    private String sourceWithCompletionSentinel(String source, Position position) {
        int offset = offsetForPosition(source, position);
        String sentinel = "__bump_completion__";
        String left = source.substring(0, offset);
        String right = source.substring(offset);
        String rewritten = left + sentinel + right;

        // While typing `receiver.` the line is often not terminated yet; add a temporary `;`
        // so parser/resolver can still run and provide completions.
        int sentinelStart = left.length();
        int cursor = sentinelStart + sentinel.length();
        while (cursor < rewritten.length()) {
            char ch = rewritten.charAt(cursor);
            if (ch == ';') {
                return rewritten;
            }
            if (ch == '\n' || ch == '\r') {
                return rewritten.substring(0, cursor) + ";" + rewritten.substring(cursor);
            }
            if (!Character.isWhitespace(ch)) {
                return rewritten;
            }
            cursor++;
        }
        return rewritten + ";";
    }

    private int offsetForPosition(String source, Position position) {
        int line = Math.max(0, position.getLine());
        int character = Math.max(0, position.getCharacter());
        int offset = 0;
        int currentLine = 0;
        while (offset < source.length() && currentLine < line) {
            if (source.charAt(offset) == '\n') {
                currentLine++;
            }
            offset++;
        }
        return Math.min(source.length(), offset + character);
    }

    private boolean startsWithPrefix(String value, String prefix) {
        return prefix == null || prefix.isEmpty() || value.startsWith(prefix);
    }

    private boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }

    private CompletionItemKind symbolKindToCompletionKind(SymbolKind kind) {
        return switch (kind) {
            case FUNCTION -> CompletionItemKind.Function;
            case CLASS -> CompletionItemKind.Class;
            case VARIABLE -> CompletionItemKind.Variable;
        };
    }

    private Diagnostic toDiagnostic(BumpException error, String source) {
        Range range = diagnosticRange(error, source);
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setRange(range);
        diagnostic.setSeverity(DiagnosticSeverity.Error);
        diagnostic.setSource("bump");
        diagnostic.setMessage(error.getCategory() + ": " + error.getDetail());
        return diagnostic;
    }

    private Range diagnosticRange(BumpException error, String source) {
        String[] lines = source.split("\n", -1);
        int lineCount = Math.max(1, lines.length);

        int rawLineIndex = error.getLine() == null ? 0 : error.getLine() - 1;
        int lineIndex = Math.max(0, Math.min(rawLineIndex, lineCount - 1));

        String lineText = lines[lineIndex];
        int lineLength = lineText.length();

        if (error.getColumn() == null) {
            int endColumn = Math.max(1, lineLength);
            return new Range(new Position(lineIndex, 0), new Position(lineIndex, endColumn));
        }

        int startColumn = Math.max(0, Math.min(error.getColumn() - 1, lineLength));
        Integer errorLength = error.getLength();
        int endColumn;
        if (errorLength != null && errorLength > 0) {
            endColumn = Math.min(lineLength, startColumn + errorLength);
            if (endColumn <= startColumn) {
                endColumn = Math.min(lineLength, startColumn + 1);
            }
        } else {
            endColumn = Math.max(startColumn + 1, lineLength);
        }

        return new Range(new Position(lineIndex, startColumn), new Position(lineIndex, endColumn));
    }

    private void appendUnsafeJavaWarnings(List<Diagnostic> diagnostics, String source) {
        List<Token> tokens;
        try {
            tokens = lexer.tokenizeForHighlighting(source);
        } catch (RuntimeException ignored) {
            return;
        }
        String[] lines = source.split("\n", -1);
        int lineCount = Math.max(1, lines.length);
        for (Token token : tokens) {
            if (token.getType() != TokenType.JAVABLOCK) {
                continue;
            }
            int lineIndex = Math.max(0, Math.min(token.getLine() - 1, lineCount - 1));
            String lineText = lines[lineIndex];
            int lineLength = lineText.length();
            int startColumn = Math.max(0, Math.min(token.getColumn() - 1, lineLength));
            int tokenLength = Math.max(1, token.getSourceLength());
            int endColumn = Math.min(lineLength, startColumn + tokenLength);
            if (endColumn <= startColumn) {
                endColumn = Math.min(lineLength, startColumn + 1);
            }

            Diagnostic warning = new Diagnostic();
            warning.setRange(new Range(new Position(lineIndex, startColumn), new Position(lineIndex, endColumn)));
            warning.setSeverity(DiagnosticSeverity.Warning);
            warning.setSource("bump");
            warning.setCode("unsafe-java");
            warning.setMessage("unsafe_java block: executes host Java code with runtime side effects.");
            diagnostics.add(warning);
        }
    }
}
