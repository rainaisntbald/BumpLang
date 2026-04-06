package bump;

import nativeClasses.NullInstance;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Environment {
    private static class VariableSlot {
        Object value;
        final BumpClass declaredType;

        VariableSlot(Object value, BumpClass declaredType) {
            this.value = value;
            this.declaredType = declaredType;
        }
    }

    private final Map<String, VariableSlot> variables = new HashMap<>();
    public final Set<String> constants = new HashSet<>();
    private final Environment parent;

    public Environment() {
        this.parent = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public void define(String name, Object value) {
        define(name, value, null, false);
    }

    public void define(String name, Object value, BumpClass declaredType) {
        define(name, value, declaredType, false);
    }

    public void define(String name, Object value, boolean isConstant) {
        define(name, value, null, isConstant);
    }

    public void define(String name, Object value, BumpClass declaredType, boolean isConstant) {
        validateAssignment(name, declaredType, value);
        if(variables.containsKey(name)) throw BumpRuntimeError.nameError("Variable '" + name + "' is already defined.");
        if(!isConstant && isReserved(name)) throw BumpRuntimeError.nameError("Cannot redefine built-in name '" + name + "'.");
        variables.put(name, new VariableSlot(value, declaredType));
        if (isConstant) {
            constants.add(name);
        }
    }

    public boolean containsLocal(String name) {
        return variables.containsKey(name);
    }

    public Object getLocal(String name) {
        VariableSlot slot = variables.get(name);
        return slot == null ? null : slot.value;
    }

    public void replaceLocal(String name, Object value) {
        VariableSlot slot = variables.get(name);
        if (slot == null) {
            throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
        }
        validateAssignment(name, slot.declaredType, value);
        slot.value = value;
    }

    public Object get(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name).value;
        }
        if (parent != null) {
            return parent.get(name);
        }
        throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
    }

    public Object getAt(int distance, String name) {
        Environment target = ancestor(distance);
        if (target == null) {
            throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
        }
        VariableSlot slot = target.variables.get(name);
        if (slot == null) {
            throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
        }
        return slot.value;
    }

    public BumpClass getType(String name) {
        if (variables.containsKey(name)) {
            Object value = variables.get(name).value;
            if (value instanceof BumpClass bumpClass) {
                return bumpClass;
            }
        }
        if (parent != null) {
            return parent.getType(name);
        }
        throw BumpRuntimeError.nameError("Type '" + name + "' is not a class.");
    }

    public void assign(String name, Object value) {
        if (constants.contains(name)) {
            throw BumpRuntimeError.nameError("Cannot assign to built-in name '" + name + "'.");
        }
        if (variables.containsKey(name)) {
            VariableSlot slot = variables.get(name);
            validateAssignment(name, slot.declaredType, value);
            slot.value = value;
            return;
        }
        if (parent != null) {
            parent.assign(name, value);
            return;
        }
        throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
    }

    public void assignAt(int distance, String name, Object value) {
        Environment target = ancestor(distance);
        if (target == null) {
            throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
        }
        VariableSlot slot = target.variables.get(name);
        if (slot == null) {
            throw BumpRuntimeError.nameError("Undefined variable '" + name + "'.");
        }
        validateAssignment(name, slot.declaredType, value);
        slot.value = value;
    }

    private void validateAssignment(String name, BumpClass declaredType, Object value) {
        if (declaredType == null) {
            return;
        }
        if (value == null || value instanceof NullInstance) {
            return;
        }
        if (value instanceof BumpInstance instance && instance.getRuntimeClass().isSubclassOf(declaredType)) {
            return;
        }
        throw BumpRuntimeError.typeError("Variable '" + name + "' must be of type " + declaredType.name + ", but got " + BumpException.describeType(value) + ".");
    }

    private boolean isReserved(String name) {
        if(constants.contains(name)) return true;

        return parent != null && parent.isReserved(name);
    }

    private Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            if (environment == null) {
                return null;
            }
            environment = environment.parent;
        }
        return environment;
    }
}
