package bump;

import ast.JavaBlockStmt;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class JavaBlockRuntime {
    private static final Map<String, JavaBlockExecutable> CACHE = new HashMap<>();

    private JavaBlockRuntime() {}

    static void execute(JavaBlockStmt stmt, Interpreter interpreter, Environment environment) {
        try {
            JavaBlockExecutable executable = CACHE.computeIfAbsent(stmt.source, JavaBlockRuntime::compile);
            executable.execute(interpreter, environment);
        } catch (BumpException error) {
            throw error;
        } catch (RuntimeException error) {
            throw BumpRuntimeError.error("Java block failed: " + error.getClass().getName() + ": " + error.getMessage());
        } catch (Exception error) {
            throw BumpRuntimeError.error("Java block failed: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private static JavaBlockExecutable compile(String source) {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw BumpRuntimeError.error("Java block support requires a JDK with the Java compiler available.");
            }

            String className = "BumpJavaBlock_" + Math.abs(source.hashCode()) + "_" + CACHE.size();
            String wrapperSource = """
import bump.*;
import nativeClasses.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;

public final class %s implements JavaBlockExecutable {
    private Object get(String name, Environment environment) {
        return unwrap(environment.get(name));
    }

    private Object getRaw(String name, Environment environment) {
        return environment.get(name);
    }

    private void set(String name, Object value, Interpreter interpreter, Environment environment) {
        environment.assign(name, wrap(value, interpreter));
    }

    private Object wrap(Object value, Interpreter interpreter) {
        if (value == null) {
            return interpreter.wrapNull();
        }
        if (value instanceof BumpInstance) {
            return value;
        }
        if (value instanceof String string) {
            return interpreter.wrapString(string);
        }
        if (value instanceof Integer integer) {
            return interpreter.wrapIntegerValue(integer);
        }
        if (value instanceof Double decimal) {
            return interpreter.wrapFloat(decimal);
        }
        if (value instanceof Boolean bool) {
            return interpreter.wrapBooleanValue(bool);
        }
        if (value instanceof Character ch) {
            return interpreter.wrapChar(ch);
        }
        throw new IllegalArgumentException("Unsupported Java value for Bump assignment: " + value.getClass().getName());
    }

    private Object unwrap(Object value) {
        if (value == null || value instanceof NullInstance) {
            return null;
        }
        if (value instanceof StringInstance stringInstance) {
            return stringInstance.value();
        }
        if (value instanceof IntegerInstance integerInstance) {
            return integerInstance.value();
        }
        if (value instanceof FloatInstance floatInstance) {
            return floatInstance.value();
        }
        if (value instanceof BoolInstance boolInstance) {
            return boolInstance.value();
        }
        if (value instanceof CharInstance charInstance) {
            return charInstance.value();
        }
        if (value instanceof ArrayInstance arrayInstance) {
            ArrayList<Object> values = new ArrayList<>();
            for (Object item : arrayInstance.values()) {
                values.add(unwrap(item));
            }
            return values;
        }
        if (value instanceof MapInstance mapInstance) {
            LinkedHashMap<Object, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < mapInstance.keys().size(); i++) {
                values.put(unwrap(mapInstance.keys().get(i)), unwrap(mapInstance.values().get(i)));
            }
            return values;
        }
        return value;
    }

    @Override
    public void execute(Interpreter interpreter, Environment environment) throws Exception {
%s
    }
}
""".formatted(className, indentSource(source));

            Path buildDir = Files.createTempDirectory("bump-java-block");
            Path sourceFile = buildDir.resolve(className + ".java");
            Files.writeString(sourceFile, wrapperSource);

            DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(buildDir.toFile()));
                Iterable<? extends javax.tools.JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile.toFile());
                String classPath = System.getProperty("java.class.path");
                List<String> options = List.of("-classpath", classPath);
                Boolean success = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
                if (!Boolean.TRUE.equals(success)) {
                    String errorMsg = formatDiagnostics(diagnostics);
                    System.err.println("Compilation failed: " + errorMsg);
                    throw BumpRuntimeError.error(errorMsg);
                }
            }

            try (URLClassLoader loader = new URLClassLoader(new URL[]{buildDir.toUri().toURL()}, JavaBlockRuntime.class.getClassLoader())) {
                Class<?> compiledClass = Class.forName(className, true, loader);
                Object instance = compiledClass.getDeclaredConstructor().newInstance();
                if (!(instance instanceof JavaBlockExecutable executable)) {
                    throw BumpRuntimeError.error("Compiled java block did not implement JavaBlockExecutable.");
                }
                return executable;
            }
        } catch (BumpException error) {
            throw error;
        } catch (IOException error) {
            throw BumpRuntimeError.error("Failed to compile java block: " + error.getMessage());
        } catch (ReflectiveOperationException error) {
            throw BumpRuntimeError.error("Failed to load compiled java block: " + error.getClass().getName() + ": " + error.getMessage());
        } catch (Exception error) {
            throw BumpRuntimeError.error("Unexpected error compiling java block: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private static String indentSource(String source) {
        String[] lines = source.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append("        ").append(line).append('\n');
        }
        return builder.toString();
    }

    private static String formatDiagnostics(DiagnosticCollector<javax.tools.JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder("Java block compilation failed:");
        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            builder.append(" [line ")
                    .append(diagnostic.getLineNumber())
                    .append("] ")
                    .append(diagnostic.getMessage(Locale.ROOT));
        }
        return builder.toString();
    }
}
