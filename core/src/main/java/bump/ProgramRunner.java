package bump;

import ast.Stmt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class ProgramRunner {
    private static final String IN_MEMORY_SOURCE_LABEL = "<source>";

    record CompletionSnapshot(Resolver resolver, Map<String, SymbolInfo> symbols) {}

    private final Lexer lexer = new Lexer();
    private final Interpreter interpreter = new Interpreter();
    private final SourcePreprocessor sourcePreprocessor = new SourcePreprocessor();

    void runPath(String path) throws IOException {
        Path scriptPath = Path.of(path).toAbsolutePath().normalize();
        runPreparedSource(sourcePreprocessor.preparePath(scriptPath));
    }

    void runSource(String source) {
        Path cwd = Path.of(".").toAbsolutePath().normalize();
        runPreparedSource(sourcePreprocessor.prepareSource(source, cwd));
    }

    private void runPreparedSource(SourcePreprocessor.PreparedSource prepared) {
        BumpException.setSource(prepared.source(), prepared.locations());
        List<Token> tokens = lexer.tokenize(prepared.source());
        List<Stmt> program = parse(tokens, prepared.visibility());
        execute(program);
    }

    private List<Stmt> parse(List<Token> tokens, ImportVisibility visibility) {
        Parser parser = new Parser(tokens);
        List<Stmt> program = parser.parseProgram();
        Resolver resolver = new Resolver(interpreter, visibility);
        resolver.resolve(program);
        return program;
    }

    private void execute(List<Stmt> program) {
        interpreter.predeclareTopLevelFunctions(program);
        for (Stmt stmt : program) {
            interpreter.execute(stmt);
        }
    }

    BumpException uncaughtThrownException(Interpreter.ThrownException error) {
        return interpreter.uncaughtThrownException(error);
    }

    List<BumpException> collectDiagnostics(String source, Path baseDirectory) {
        try {
            SourcePreprocessor.PreparedSource prepared = sourcePreprocessor.prepareSource(source, baseDirectory);
            BumpException.setSource(prepared.source(), prepared.locations());
            List<Token> tokens = lexer.tokenize(prepared.source());
            return parseForDiagnostics(tokens, prepared.visibility());
        } catch (BumpException error) {
            return List.of(error);
        }
    }

    private List<BumpException> parseForDiagnostics(List<Token> tokens, ImportVisibility visibility) {
        Parser parser = new Parser(tokens);
        List<Stmt> program = parser.parseProgram();
        Resolver resolver = new Resolver(interpreter, visibility);
        return resolver.resolveWithRecovery(program);
    }

    CompletionSnapshot completionSnapshot(String source, Path baseDirectory, int sourceLine) {
        try {
            SourcePreprocessor.PreparedSource prepared = sourcePreprocessor.prepareSource(source, baseDirectory);
            BumpException.setSource(prepared.source(), prepared.locations());
            List<Token> tokens = lexer.tokenize(prepared.source());
            Parser parser = new Parser(tokens);
            List<Stmt> program = parser.parseProgram();
            Resolver resolver = new Resolver(interpreter, prepared.visibility());
            resolver.resolveWithRecovery(program);
            Map<String, SymbolInfo> visible = resolver.snapshotVisibleSymbols(IN_MEMORY_SOURCE_LABEL, sourceLine);
            return new CompletionSnapshot(resolver, visible);
        } catch (BumpException error) {
            return new CompletionSnapshot(null, Map.of());
        }
    }
}
