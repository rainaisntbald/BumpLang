package bump;

import nativeClasses.ArrayClass;
import nativeClasses.BoolClass;
import nativeClasses.CharClass;
import nativeClasses.ClassClass;
import nativeClasses.FileClass;
import nativeClasses.FloatClass;
import nativeClasses.FunctionClass;
import nativeClasses.IntegerClass;
import nativeClasses.MapClass;
import nativeClasses.NullClass;
import nativeClasses.ObjectClass;
import nativeClasses.StringClass;
import nativeFunctions.Bool;
import nativeFunctions.Implements;
import nativeFunctions.Input;
import nativeFunctions.Int;
import nativeFunctions.Print;
import nativeFunctions.Str;
import nativeFunctions.Throw;
import nativeFunctions.TypeOf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Builtins {
    public record ClassDefinition(String name, boolean extendable, String superclassName, List<String> interfaceNames) {}

    public record FunctionDefinition(String name, String returnTypeName, List<String> parameterTypeNames) {}

    public record RuntimeState(
            ClassClass classClass,
            FunctionClass functionClass,
            ObjectClass objectClass,
            BumpClass exceptionClass
    ) {}

    private record CoreRuntimeClasses(
            ClassClass classClass,
            FunctionClass functionClass,
            ObjectClass objectClass
    ) {}

    private record RuntimeClasses(
            BumpClass comparableClass,
            ArrayClass arrayClass,
            BoolClass boolClass,
            CharClass charClass,
            NullClass nullClass,
            StringClass stringClass,
            IntegerClass integerClass,
            FloatClass floatClass,
            MapClass mapClass,
            FileClass fileClass,
            BumpClass exceptionClass,
            BumpClass typeErrorClass,
            BumpClass nameErrorClass,
            BumpClass propertyErrorClass,
            BumpClass indexErrorClass,
            BumpClass valueErrorClass,
            BumpClass arityErrorClass,
            BumpClass divisionByZeroErrorClass
    ) {}

    public static final String OBJECT = "Object";
    public static final String COMPARABLE = "Comparable";
    public static final String CLASS = "Class";
    public static final String FUNCTION = "Function";
    public static final String ARRAY = "Array";
    public static final String BOOL = "Bool";
    public static final String CHAR = "Char";
    public static final String NULL = "Null";
    public static final String STRING = "String";
    public static final String INTEGER = "Integer";
    public static final String FLOAT = "Float";
    public static final String DATETIME = "DateTime";
    public static final String MAP = "Map";
    public static final String FILE = "File";
    public static final String EXCEPTION = "Exception";
    public static final String TYPE_ERROR = "TypeError";
    public static final String NAME_ERROR = "NameError";
    public static final String PROPERTY_ERROR = "PropertyError";
    public static final String INDEX_ERROR = "IndexError";
    public static final String VALUE_ERROR = "ValueError";
    public static final String ARITY_ERROR = "ArityError";
    public static final String DIVISION_BY_ZERO_ERROR = "DivisionByZeroError";
    public static final String EXCEPTION_MESSAGE = "message";
    public static final String EXCEPTION_LINE = "line";
    public static final String EXCEPTION_COLUMN = "column";
    public static final String EXCEPTION_CONTEXT = "context";
    public static final String EXCEPTION_STACK_TRACE = "stack_trace";

    private static final List<ClassDefinition> CLASS_DEFINITIONS = List.of(
            new ClassDefinition(OBJECT, false, null, List.of()),
            new ClassDefinition(COMPARABLE, true, OBJECT, List.of()),
            new ClassDefinition(CLASS, false, OBJECT, List.of()),
            new ClassDefinition(FUNCTION, false, OBJECT, List.of()),
            new ClassDefinition(ARRAY, false, OBJECT, List.of()),
            new ClassDefinition(BOOL, false, OBJECT, List.of()),
            new ClassDefinition(CHAR, false, OBJECT, List.of()),
            new ClassDefinition(NULL, false, OBJECT, List.of()),
            new ClassDefinition(STRING, false, OBJECT, List.of()),
            new ClassDefinition(INTEGER, false, OBJECT, List.of(COMPARABLE)),
            new ClassDefinition(FLOAT, false, OBJECT, List.of(COMPARABLE)),
            new ClassDefinition(DATETIME, false, OBJECT, List.of(COMPARABLE)),
            new ClassDefinition(MAP, false, OBJECT, List.of()),
            new ClassDefinition(FILE, false, OBJECT, List.of()),
            new ClassDefinition(EXCEPTION, true, OBJECT, List.of()),
            new ClassDefinition(TYPE_ERROR, true, EXCEPTION, List.of()),
            new ClassDefinition(NAME_ERROR, true, EXCEPTION, List.of()),
            new ClassDefinition(PROPERTY_ERROR, true, EXCEPTION, List.of()),
            new ClassDefinition(INDEX_ERROR, true, EXCEPTION, List.of()),
            new ClassDefinition(VALUE_ERROR, true, EXCEPTION, List.of()),
            new ClassDefinition(ARITY_ERROR, true, EXCEPTION, List.of()),
            new ClassDefinition(DIVISION_BY_ZERO_ERROR, true, VALUE_ERROR, List.of())
    );

    private static final List<FunctionDefinition> FUNCTION_DEFINITIONS = List.of(
            new FunctionDefinition("print", NULL, List.of(OBJECT)),
            new FunctionDefinition("input", STRING, List.of()),
            new FunctionDefinition("int", INTEGER, List.of(OBJECT)),
            new FunctionDefinition("str", STRING, List.of(OBJECT)),
            new FunctionDefinition("bool", BOOL, List.of(OBJECT)),
            new FunctionDefinition("type_of", CLASS, List.of(OBJECT)),
            new FunctionDefinition("throw", NULL, List.of(EXCEPTION)),
            new FunctionDefinition("implements", BOOL, List.of(OBJECT, CLASS))
    );

    private Builtins() {}

    public static List<ClassDefinition> classDefinitions() {
        return CLASS_DEFINITIONS;
    }

    public static List<FunctionDefinition> functionDefinitions() {
        return FUNCTION_DEFINITIONS;
    }

    public static RuntimeState installRuntime(Environment globals) {
        CoreRuntimeClasses core = createCoreRuntime(globals);
        RuntimeClasses runtimeClasses = createRuntimeClasses(core.classClass(), core.objectClass(), core.functionClass());
        installNativeFunctions(globals, core.functionClass());
        installNativeClasses(globals, runtimeClasses);
        return new RuntimeState(core.classClass(), core.functionClass(), core.objectClass(), runtimeClasses.exceptionClass());
    }

    private static CoreRuntimeClasses createCoreRuntime(Environment globals) {
        ClassClass classClass = new ClassClass();
        classClass.setRuntimeClass(classClass);

        ObjectClass objectClass = new ObjectClass(classClass);
        FunctionClass functionClass = new FunctionClass(classClass, objectClass);
        objectClass.installMethods(functionClass);
        classClass.setSuperclass(objectClass);

        globals.define(OBJECT, objectClass, true);
        globals.define(CLASS, classClass, true);
        globals.define(FUNCTION, functionClass, true);
        return new CoreRuntimeClasses(classClass, functionClass, objectClass);
    }

    private static RuntimeClasses createRuntimeClasses(ClassClass classClass, ObjectClass objectClass, FunctionClass functionClass) {
        BumpClass comparableClass = new BumpClass(
                classClass, COMPARABLE, new HashMap<>(), new HashMap<>(), new HashMap<>(), objectClass, List.of(), null, false, true
        );
        ArrayClass arrayClass = new ArrayClass(classClass, objectClass, functionClass);
        BoolClass boolClass = new BoolClass(classClass, objectClass, functionClass);
        CharClass charClass = new CharClass(classClass, objectClass, functionClass);
        NullClass nullClass = new NullClass(classClass, objectClass);
        StringClass stringClass = new StringClass(classClass, objectClass, functionClass);
        IntegerClass integerClass = new IntegerClass(classClass, objectClass, List.of(comparableClass), functionClass);
        FloatClass floatClass = new FloatClass(classClass, objectClass, List.of(comparableClass), functionClass);
        MapClass mapClass = new MapClass(classClass, objectClass, functionClass);
        FileClass fileClass = new FileClass(classClass, objectClass, functionClass);
        Map<String, BumpClass> exceptionFields = buildExceptionFields(stringClass, integerClass, arrayClass);
        BumpClass exceptionClass = new BumpClass(classClass, EXCEPTION, exceptionFields, new HashMap<>(), objectClass, true);
        BumpClass typeErrorClass = new BumpClass(classClass, TYPE_ERROR, exceptionFields, new HashMap<>(), exceptionClass, true);
        BumpClass nameErrorClass = new BumpClass(classClass, NAME_ERROR, exceptionFields, new HashMap<>(), exceptionClass, true);
        BumpClass propertyErrorClass = new BumpClass(classClass, PROPERTY_ERROR, exceptionFields, new HashMap<>(), exceptionClass, true);
        BumpClass indexErrorClass = new BumpClass(classClass, INDEX_ERROR, exceptionFields, new HashMap<>(), exceptionClass, true);
        BumpClass valueErrorClass = new BumpClass(classClass, VALUE_ERROR, exceptionFields, new HashMap<>(), exceptionClass, true);
        BumpClass arityErrorClass = new BumpClass(classClass, ARITY_ERROR, exceptionFields, new HashMap<>(), exceptionClass, true);
        BumpClass divisionByZeroErrorClass = new BumpClass(classClass, DIVISION_BY_ZERO_ERROR, exceptionFields, new HashMap<>(), valueErrorClass, true);

        return new RuntimeClasses(
                comparableClass, arrayClass, boolClass, charClass, nullClass, stringClass, integerClass, floatClass, mapClass, fileClass,
                exceptionClass, typeErrorClass, nameErrorClass, propertyErrorClass, indexErrorClass, valueErrorClass, arityErrorClass, divisionByZeroErrorClass
        );
    }

    private static Map<String, BumpClass> buildExceptionFields(StringClass stringClass, IntegerClass integerClass, ArrayClass arrayClass) {
        Map<String, BumpClass> fields = new HashMap<>();
        fields.put(EXCEPTION_MESSAGE, stringClass);
        fields.put(EXCEPTION_LINE, integerClass);
        fields.put(EXCEPTION_COLUMN, integerClass);
        fields.put(EXCEPTION_CONTEXT, stringClass);
        fields.put(EXCEPTION_STACK_TRACE, arrayClass);
        return fields;
    }

    private static void installNativeFunctions(Environment globals, FunctionClass functionClass) {
        globals.define("print", new Print(functionClass), true);
        globals.define("input", new Input(functionClass), true);
        globals.define("int", new Int(functionClass), true);
        globals.define("str", new Str(functionClass), true);
        globals.define("bool", new Bool(functionClass), true);
        globals.define("type_of", new TypeOf(functionClass), true);
        globals.define("throw", new Throw(functionClass), true);
        globals.define("implements", new Implements(functionClass), true);
    }

    private static void installNativeClasses(Environment globals, RuntimeClasses classes) {
        globals.define(ARRAY, classes.arrayClass(), true);
        globals.define(BOOL, classes.boolClass(), true);
        globals.define(CHAR, classes.charClass(), true);
        globals.define(COMPARABLE, classes.comparableClass(), true);
        globals.define(TYPE_ERROR, classes.typeErrorClass(), true);
        globals.define(NAME_ERROR, classes.nameErrorClass(), true);
        globals.define(PROPERTY_ERROR, classes.propertyErrorClass(), true);
        globals.define(INDEX_ERROR, classes.indexErrorClass(), true);
        globals.define(VALUE_ERROR, classes.valueErrorClass(), true);
        globals.define(ARITY_ERROR, classes.arityErrorClass(), true);
        globals.define(DIVISION_BY_ZERO_ERROR, classes.divisionByZeroErrorClass(), true);
        globals.define(EXCEPTION, classes.exceptionClass(), true);
        globals.define(NULL, classes.nullClass(), true);
        globals.define(STRING, classes.stringClass(), true);
        globals.define(INTEGER, classes.integerClass(), true);
        globals.define(FLOAT, classes.floatClass(), true);
        globals.define(MAP, classes.mapClass(), true);
        globals.define(FILE, classes.fileClass(), true);
    }
}
