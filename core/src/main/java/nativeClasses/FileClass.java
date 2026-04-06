package nativeClasses;

import bump.BumpClass;
import bump.BumpException;
import bump.BumpMethod;
import bump.BumpRuntimeError;
import bump.Interpreter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileClass extends BumpClass {
    public FileClass(BumpClass runtimeClass, ObjectClass objectClass, BumpClass functionClass) {
        super(runtimeClass, "File", new HashMap<>(), createMethods(functionClass), objectClass, true);
    }

    private static Map<String, List<BumpMethod>> createMethods(BumpClass functionClass) {
        Map<String, List<BumpMethod>> methods = new HashMap<>();
        methods.put("open", java.util.List.of(fileMethod(functionClass, "open", 0, self -> (interpreter, arguments) -> {
            self.open();
            return self;
        })));
        methods.put("contents", java.util.List.of(fileMethod(functionClass, "contents", 0, self -> (interpreter, arguments) -> {
            ensureOpen(self);
            try {
                if (!Files.exists(self.path())) {
                    return interpreter.wrapString("");
                }
                return interpreter.wrapString(Files.readString(self.path()));
            } catch (IOException error) {
                throw BumpRuntimeError.valueError("Could not read file '" + self.path() + "': " + error.getMessage());
            }
        })));
        methods.put("set_contents", java.util.List.of(fileMethod(functionClass, "set_contents", 1, self -> (interpreter, arguments) -> {
            ensureOpen(self);
            if (!(arguments.get(0) instanceof StringInstance stringInstance)) {
                throw BumpRuntimeError.typeError("File.set_contents() expects a String, but got " + BumpException.describeType(arguments.get(0)) + ".");
            }
            try {
                Path parent = self.path().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(self.path(), stringInstance.value());
                return interpreter.wrapNull();
            } catch (IOException error) {
                throw BumpRuntimeError.valueError("Could not write file '" + self.path() + "': " + error.getMessage());
            }
        })));
        methods.put("close", java.util.List.of(fileMethod(functionClass, "close", 0, self -> (interpreter, arguments) -> {
            ensureOpen(self);
            self.close();
            return interpreter.wrapNull();
        })));
        methods.put("get_path", java.util.List.of(fileMethod(functionClass, "get_path", 0, self -> (interpreter, arguments) -> {
            return interpreter.wrapString(self.path().toString());
        })));
        methods.put("create", java.util.List.of(fileMethod(functionClass, "create", 0, self -> (interpreter, arguments) -> {
            try {
                Files.createFile(self.path());
            } catch (IOException error) {
                throw BumpRuntimeError.valueError("Could not create file '" + self.path() + "': " + error.getMessage());
            }
            return interpreter.wrapNull();
        })));
        return methods;
    }

    private static void ensureOpen(FileInstance self) {
        if (!self.isOpen()) {
            throw BumpRuntimeError.valueError("File '" + self.path() + "' is closed.");
        }
    }

    private static NativeMethod fileMethod(BumpClass functionClass, String name, int arity, java.util.function.Function<FileInstance, NativeMethod.BoundCall> binder) {
        return new NativeMethod(functionClass, name, arity, instance -> {
            if (!(instance instanceof FileInstance fileInstance)) {
                throw BumpException.internal("File method called on non-File instance.");
            }
            return binder.apply(fileInstance);
        });
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        Object value = arguments.get(0);
        if (value instanceof StringInstance stringInstance) {
            return new FileInstance(this, Path.of(stringInstance.value()));
        }
        if (value instanceof String string) {
            return new FileInstance(this, Path.of(string));
        }
        throw BumpRuntimeError.typeError("File() expects a String value, but got " + BumpException.describeType(value) + ".");
    }
}
