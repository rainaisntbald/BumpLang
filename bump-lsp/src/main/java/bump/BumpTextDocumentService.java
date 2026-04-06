package bump;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensRangeParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class BumpTextDocumentService implements TextDocumentService {
    private final ProgramRunner analyzer = new ProgramRunner();
    private final Lexer lexer = new Lexer();
    private final boolean emitUnsafeJavaWarnings;
    private final Map<String, String> openDocuments = new HashMap<>();
    private LanguageClient client;

    BumpTextDocumentService() {
        this(true);
    }

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
