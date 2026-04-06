package bump;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

public final class BumpLanguageServerLauncher {
    private BumpLanguageServerLauncher() {}

    public static void main(String[] args) {
        boolean emitUnsafeJavaWarnings = true;
        for (String arg : args) {
            if ("--no-unsafe-java-warnings".equals(arg)) {
                emitUnsafeJavaWarnings = false;
                continue;
            }
            if ("--help".equals(arg) || "-h".equals(arg)) {
                System.err.println("Usage: bump-lsp [--no-unsafe-java-warnings]");
                return;
            }
            System.err.println("Ignoring unknown argument: " + arg);
        }

        BumpLanguageServer server = new BumpLanguageServer(emitUnsafeJavaWarnings);
        Launcher<LanguageClient> launcher = Launcher.createLauncher(
                server,
                LanguageClient.class,
                System.in,
                System.out
        );
        server.connect(launcher.getRemoteProxy());
        launcher.startListening();
    }
}
