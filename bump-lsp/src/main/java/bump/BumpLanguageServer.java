package bump;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.concurrent.CompletableFuture;

public final class BumpLanguageServer implements LanguageServer, LanguageClientAware {
    private final BumpTextDocumentService textDocumentService;
    private final WorkspaceService workspaceService = new BumpWorkspaceService();

    public BumpLanguageServer() {
        this(true);
    }

    public BumpLanguageServer(boolean emitUnsafeJavaWarnings) {
        this.textDocumentService = new BumpTextDocumentService(emitUnsafeJavaWarnings);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        TextDocumentSyncOptions syncOptions = new TextDocumentSyncOptions();
        syncOptions.setOpenClose(true);
        syncOptions.setChange(TextDocumentSyncKind.Full);
        capabilities.setTextDocumentSync(syncOptions);
        SemanticTokensWithRegistrationOptions semanticTokens = new SemanticTokensWithRegistrationOptions(BumpSemanticTokens.legend(), true);
        semanticTokens.setRange(true);
        capabilities.setSemanticTokensProvider(semanticTokens);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.connect(client);
    }
}
