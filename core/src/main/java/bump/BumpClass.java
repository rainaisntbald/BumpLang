package bump;

import ast.Expr;
import nativeClasses.NullInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BumpClass extends BumpInstance implements BumpCallable {
    public final String name;
    protected final Map<String, BumpClass> fields;
    protected final Map<String, Expr> fieldInitializers;
    protected final Map<String, List<BumpMethod>> methods;
    protected BumpClass superclass;
    protected final List<BumpClass> interfaces;
    protected final List<String> typeParameterNames;
    protected final Map<String, BumpInstance> enumValues = new HashMap<>();
    protected final Map<String, BumpCallable> enumConstructors = new HashMap<>();
    private final boolean nativeClass;
    private final boolean abstractClass;
    private final Environment fieldInitializerClosure;
    private boolean enumClass = false;

    public BumpClass(BumpClass runtimeClass, String name, Map<String, BumpClass> fields, Map<String, List<BumpMethod>> methods, BumpClass superclass) {
        this(runtimeClass, name, fields, new HashMap<>(), methods, superclass, List.of(), null, false, false, List.of());
    }

    public BumpClass(BumpClass runtimeClass, String name, Map<String, BumpClass> fields, Map<String, List<BumpMethod>> methods, BumpClass superclass, boolean nativeClass) {
        this(runtimeClass, name, fields, new HashMap<>(), methods, superclass, List.of(), null, nativeClass, false, List.of());
    }

    public BumpClass(BumpClass runtimeClass, String name, Map<String, BumpClass> fields, Map<String, Expr> fieldInitializers, Map<String, List<BumpMethod>> methods, BumpClass superclass, Environment fieldInitializerClosure, boolean nativeClass) {
        this(runtimeClass, name, fields, fieldInitializers, methods, superclass, List.of(), fieldInitializerClosure, nativeClass, false, List.of());
    }

    public BumpClass(BumpClass runtimeClass, String name, Map<String, BumpClass> fields, Map<String, Expr> fieldInitializers, Map<String, List<BumpMethod>> methods, BumpClass superclass, List<BumpClass> interfaces, Environment fieldInitializerClosure, boolean nativeClass, boolean abstractClass) {
        this(runtimeClass, name, fields, fieldInitializers, methods, superclass, interfaces, fieldInitializerClosure, nativeClass, abstractClass, List.of());
    }

    public BumpClass(BumpClass runtimeClass, String name, Map<String, BumpClass> fields, Map<String, Expr> fieldInitializers, Map<String, List<BumpMethod>> methods, BumpClass superclass, List<BumpClass> interfaces, Environment fieldInitializerClosure, boolean nativeClass, boolean abstractClass, List<String> typeParameterNames) {
        super(runtimeClass);
        this.name = name;
        this.fields = fields;
        this.fieldInitializers = fieldInitializers;
        this.methods = methods;
        this.superclass = superclass;
        this.interfaces = List.copyOf(interfaces);
        this.typeParameterNames = List.copyOf(typeParameterNames);
        this.fieldInitializerClosure = fieldInitializerClosure;
        this.nativeClass = nativeClass;
        this.abstractClass = abstractClass;
    }

    public List<String> getTypeParameterNames() {
        return typeParameterNames;
    }

    public List<BumpMethod> findMethods(String name) {
        List<BumpMethod> methodList = methods.get(name);
        List<BumpMethod> inherited = superclass != null ? superclass.findMethods(name) : List.of();
        
        List<BumpMethod> interfaceMethods = new ArrayList<>();
        for (BumpClass iface : interfaces) {
            for (BumpMethod interfaceMethod : iface.findMethods(name)) {
                if (!containsMatchingSignature(interfaceMethods, interfaceMethod)) {
                    interfaceMethods.add(interfaceMethod);
                }
            }
        }

        if ((methodList == null || methodList.isEmpty()) && inherited.isEmpty()) {
            return interfaceMethods;
        }

        List<BumpMethod> combined = new ArrayList<>();
        if (methodList != null) {
            combined.addAll(methodList);
        }
        for (BumpMethod inheritedMethod : inherited) {
            if (!containsMatchingSignature(combined, inheritedMethod)) {
                combined.add(inheritedMethod);
            }
        }

        for (BumpMethod interfaceMethod : interfaceMethods) {
            if (!containsMatchingSignature(combined, interfaceMethod)) {
                combined.add(interfaceMethod);
            }
        }
        return combined;
    }

    public BumpClass getSuperclass() {
        return superclass;
    }

    public void setSuperclass(BumpClass superclass) {
        this.superclass = superclass;
    }

    public boolean isNativeClass() {
        return nativeClass;
    }

    public boolean isAbstractClass() {
        return abstractClass;
    }

    public void markAsEnum() {
        enumClass = true;
    }

    public boolean isEnumClass() {
        return enumClass;
    }

    public void defineEnumValue(String name, BumpInstance value) {
        enumValues.put(name, value);
    }

    public void defineEnumConstructor(String name, BumpCallable constructor) {
        enumConstructors.put(name, constructor);
    }

    public boolean isSubclassOf(BumpClass runtimeClass) {
        if(this == runtimeClass) return true;
        if (superclass != null && superclass.isSubclassOf(runtimeClass)) {
            return true;
        }
        for (BumpClass iface : interfaces) {
            if (iface != null && iface.isSubclassOf(runtimeClass)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNamedOrSubclassOf(String className) {
        if (name.equals(className)) {
            return true;
        }
        if (superclass != null && superclass.isNamedOrSubclassOf(className)) {
            return true;
        }
        for (BumpClass iface : interfaces) {
            if (iface != null && iface.isNamedOrSubclassOf(className)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasField(String name) {
        if(fields.containsKey(name)) return true;
        if(superclass != null) return superclass.hasField(name);
        return false;
    }

    public boolean hasOwnField(String name) {
        return fields.containsKey(name);
    }

    public boolean hasOwnMethod(String name) {
        return methods.containsKey(name) && !methods.get(name).isEmpty();
    }

    public boolean hasInheritedField(String name) {
        return superclass != null && superclass.hasField(name);
    }

    public boolean hasInheritedMethod(String name) {
        return superclass != null && !superclass.findMethods(name).isEmpty();
    }

    public void defineMethod(String name, BumpMethod method) {
        if (hasField(name)) {
            throw BumpRuntimeError.propertyError("Cannot define method '" + name + "' on class '" + this.name + "' because it conflicts with an existing field.");
        }
        if (containsMatchingSignature(findMethods(name), method)) {
            throw BumpRuntimeError.propertyError("Method '" + name + "' already has an overload with the same parameter types.");
        }
        methods.computeIfAbsent(name, key -> new ArrayList<>()).add(method);
    }

    public BumpClass findFieldType(String name) {
        BumpClass fieldType = fields.get(name);
        if (fieldType != null) {
            return fieldType;
        }
        if (superclass != null) {
            return superclass.findFieldType(name);
        }
        return null;
    }

    public void validateFieldAssignment(String name, Object value) {
        if (!hasField(name)) {
            throw BumpRuntimeError.propertyError("Field '" + name + "' is not declared on class '" + this.name + "'.");
        }

        BumpClass fieldType = findFieldType(name);
        if (fieldType == null || value == null || value instanceof NullInstance) {
            return;
        }
        if (value instanceof BumpInstance instance && instance.getRuntimeClass().isSubclassOf(fieldType)) {
            return;
        }
        throw BumpRuntimeError.typeError("Field '" + name + "' on class '" + this.name + "' must be of type " + fieldType.name + ", but got " + BumpException.describeType(value) + ".");
    }

    protected void initializeFields(BumpInstance instance, Interpreter interpreter) {
        if (superclass != null) {
            superclass.initializeFields(instance, interpreter);
        }
        for (String fieldName : fields.keySet()) {
            instance.defineField(fieldName, interpreter.wrapNull());
            Expr initializer = fieldInitializers.get(fieldName);
            if (initializer != null) {
                Environment previous = interpreter.environment;
                try {
                    Environment initializerEnvironment = fieldInitializerClosure != null
                            ? new Environment(fieldInitializerClosure)
                            : new Environment(interpreter.globals);
                    initializerEnvironment.define("this", instance);
                    interpreter.environment = initializerEnvironment;
                    instance.set(fieldName, interpreter.eval(initializer));
                } finally {
                    interpreter.environment = previous;
                }
            }
        }
    }

    @Override
    public int arity() {
        List<BumpMethod> initializers = findMethods("init");
        if (initializers.isEmpty()) return 0;
        if (initializers.size() == 1) {
            return initializers.get(0).arity();
        }
        return -1;
    }

    @Override
    public boolean acceptsArity(int argumentCount) {
        List<BumpMethod> initializers = findMethods("init");
        if (initializers.isEmpty()) {
            return arity() == argumentCount;
        }
        return initializers.stream().anyMatch(initializer -> initializer.acceptsArity(argumentCount));
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        if (enumClass) {
            throw BumpRuntimeError.typeError("Cannot instantiate enum '" + name + "'.");
        }
        if (abstractClass) {
            throw BumpRuntimeError.typeError("Cannot instantiate abstract class '" + name + "'.");
        }
        BumpInstance instance = new BumpInstance(this);

        if (interpreter.pendingTypeArguments != null && !interpreter.pendingTypeArguments.isEmpty()) {
            for (Map.Entry<String, BumpClass> entry : interpreter.pendingTypeArguments.entrySet()) {
                instance.setTypeArgument(entry.getKey(), entry.getValue());
            }
        }

        initializeFields(instance, interpreter);
        List<BumpMethod> initializers = findMethods("init");
        if (!initializers.isEmpty()) {
            List<BumpCallable> boundInitializers = initializers.stream()
                    .map(initializer -> initializer.bind(instance))
                    .toList();
            OverloadedFunction.selectCallable("init", boundInitializers, arguments).call(interpreter, arguments);
        }
        return instance;
    }

    @Override
    public Object get(String name) {
        if (enumConstructors.containsKey(name)) {
            return enumConstructors.get(name);
        }
        if (enumValues.containsKey(name)) {
            return enumValues.get(name);
        }
        return super.get(name);
    }

    private boolean containsMatchingSignature(List<BumpMethod> methods, BumpMethod candidate) {
        for (BumpMethod method : methods) {
            if (method instanceof BumpFunction function && candidate instanceof BumpFunction candidateFunction) {
                if (function.declaration.hasSameParameterTypes(candidateFunction.declaration)) {
                    return true;
                }
                continue;
            }
            if (method.arity() == candidate.arity()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "<class " + name + ">";
    }
}
