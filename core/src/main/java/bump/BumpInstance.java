package bump;

import nativeClasses.NullInstance;

import java.util.HashMap;
import java.util.Map;

public class BumpInstance {
    protected BumpClass runtimeClass;
    protected final Map<String, Object> fields = new HashMap<>();
    protected final Map<String, BumpClass> typeArguments = new HashMap<>();

    public BumpInstance(BumpClass runtimeClass) {
        this.runtimeClass = runtimeClass;
    }

    public void setTypeArgument(String name, BumpClass typeClass) {
        typeArguments.put(name, typeClass);
    }

    public BumpClass getTypeArgument(String name) {
        return typeArguments.get(name);
    }

    public BumpClass getRuntimeClass() {
        return runtimeClass;
    }

    public void setRuntimeClass(BumpClass runtimeClass) {
        this.runtimeClass = runtimeClass;
    }

    public Object get(String name) {
        if (fields.containsKey(name)) {
            return fields.get(name);
        }

        java.util.List<BumpMethod> methods = runtimeClass.findMethods(name);
        if (!methods.isEmpty()) {
            if (methods.size() == 1) {
                return methods.get(0).bind(this);
            }
            java.util.List<BumpCallable> bound = methods.stream().map(method -> method.bind(this)).toList();
            BumpCallable first = bound.get(0);
            BumpClass functionClass = first instanceof BumpInstance instance ? instance.getRuntimeClass() : runtimeClass.getRuntimeClass();
            return new OverloadedFunction(functionClass, name, bound);
        }

        throw BumpRuntimeError.propertyError("Undefined property '" + name + "'.");
    }

    public void defineField(String name, Object value) {
        if(!runtimeClass.findMethods(name).isEmpty()) throw BumpRuntimeError.propertyError("Cannot store field '" + name + "' because it conflicts with an existing method.");
        runtimeClass.validateFieldAssignment(name, value);
        fields.put(name, value);
    }

    public void set(String name, Object value) {
        if(!runtimeClass.findMethods(name).isEmpty()) throw BumpRuntimeError.propertyError("Cannot assign to field '" + name + "' because it conflicts with an existing method.");
        runtimeClass.validateFieldAssignment(name, value);
        fields.put(name, value);
    }

    @Override
    public String toString() {
        if (runtimeClass.isNamedOrSubclassOf(Builtins.EXCEPTION)) {
            Object message = fields.get(Builtins.EXCEPTION_MESSAGE);
            if (message instanceof nativeClasses.StringInstance stringInstance && !stringInstance.value().isEmpty()) {
                return runtimeClass.name + ": " + stringInstance.value();
            }
            return runtimeClass.name;
        }
        return runtimeClass.name + " instance";
    }
}
