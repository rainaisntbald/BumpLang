package bump;

import ast.Stmt;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class SemanticTestHarness {
    enum RunMode {
        RAW,
        WITH_STDLIB_IMPORTS,
        WITH_IMPORT_DIRECTIVES
    }

    record RunResult(String output, Throwable error) {}

    private static final String STDLIB_IMPORTS = """
            import stdlib.option;
            import stdlib.math;
            import stdlib.strings;
            import stdlib.dateTime;
            import stdlib.iterable;
            import stdlib.arrayList;
            import stdlib.collections;
            import stdlib.io;
            import stdlib.result;
            import stdlib.testing;
            """;

    private SemanticTestHarness() {}

    static RunResult run(String source, RunMode mode) {
        synchronized (SemanticTestHarness.class) {
            PrintStream originalOut = System.out;
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            PrintStream capturingOut = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8);
            Interpreter interpreter = null;
            ProgramRunner runner = null;

            try {
                System.setOut(capturingOut);
                switch (mode) {
                    case RAW -> {
                        interpreter = new Interpreter();
                        runRaw(source, interpreter);
                    }
                    case WITH_STDLIB_IMPORTS -> {
                        runner = new ProgramRunner();
                        runWithImports(STDLIB_IMPORTS + source, runner);
                    }
                    case WITH_IMPORT_DIRECTIVES -> {
                        runner = new ProgramRunner();
                        runWithImports(source, runner);
                    }
                }
                return success(outputBuffer, capturingOut);
            } catch (Interpreter.ThrownException error) {
                String output = flushOutput(outputBuffer, capturingOut);
                if (runner != null) {
                    return new RunResult(output, runner.uncaughtThrownException(error));
                }
                if (interpreter != null) {
                    return new RunResult(output, interpreter.uncaughtThrownException(error));
                }
                return new RunResult(output, error);
            } catch (BumpException error) {
                return failure(outputBuffer, capturingOut, error);
            } catch (Throwable error) {
                return failure(outputBuffer, capturingOut, error);
            } finally {
                System.setOut(originalOut);
                capturingOut.close();
            }
        }
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n").trim();
    }

    private static void runRaw(String source, Interpreter interpreter) {
        BumpException.setSource(source);
        Lexer lexer = new Lexer();
        Parser parser = new Parser(lexer.tokenize(source));
        List<Stmt> program = parser.parseProgram();
        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(program);
        for (Stmt stmt : program) {
            interpreter.execute(stmt);
        }
    }

    private static void runWithImports(String source, ProgramRunner runner) {
        runner.runSource(source);
    }

    private static RunResult success(ByteArrayOutputStream outputBuffer, PrintStream capturingOut) {
        return new RunResult(flushOutput(outputBuffer, capturingOut), null);
    }

    private static RunResult failure(ByteArrayOutputStream outputBuffer, PrintStream capturingOut, Throwable error) {
        return new RunResult(flushOutput(outputBuffer, capturingOut), error);
    }

    private static String flushOutput(ByteArrayOutputStream outputBuffer, PrintStream capturingOut) {
        capturingOut.flush();
        return outputBuffer.toString(StandardCharsets.UTF_8);
    }
}
