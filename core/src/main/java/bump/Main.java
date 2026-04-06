package bump;

import ast.Stmt;
import java.io.IOException;
import java.util.Arrays;

public class Main {
    private static final String DEFAULT_PROGRAM = """
            print("Hello, world!");
            """;

    public static void main(String[] args) {
        ProgramRunner runner = new ProgramRunner();

        try {
            if (args.length > 0) {
                runner.runPath(args[0]);
            } else {
                runner.runSource(DEFAULT_PROGRAM);
            }
        } catch (Interpreter.ThrownException error) {
            System.err.println(runner.uncaughtThrownException(error).getMessage());
            System.exit(65);
        } catch (Throwable error) {
            if (error instanceof BumpException bumpError) {
                System.err.println(bumpError.getMessage());
                System.exit(65);
            }
            if (error instanceof IOException ioError) {
                System.err.println("I/O error: could not read file '" + args[0] + "'.");
                System.exit(65);
            }
            System.err.println(BumpException.internal(Arrays.toString(error.getStackTrace())).getMessage());
            System.exit(70);
        }
    }
}
