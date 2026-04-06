package nativeFunctions;

import bump.BumpClass;
import bump.Interpreter;
import bump.NativeFunction;

import java.util.List;
import java.util.Scanner;

public class Input extends NativeFunction {
    private static Scanner scanner = new Scanner(System.in);

    public Input(BumpClass runtimeClass) {
        super(runtimeClass, "input", 0);
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        return interpreter.wrapString(scanner.nextLine());
    }
}
