package bump;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ClassInfo {
    final String name;
    public boolean nativeClass;
    final boolean extendable;
    final boolean abstractClass;
    final List<SemanticType> typeParameters;
    final ClassInfo superclass;
    final List<ClassInfo> interfaces;
    final SemanticType semanticType;
    final Map<String, SemanticType> fields = new HashMap<>();
    final Map<String, Boolean> privateFields = new HashMap<>();
    final Map<String, List<SemanticType>> methods = new HashMap<>();
    final Map<String, Boolean> privateMethods = new HashMap<>();
    final Map<String, List<SemanticType>> concreteMethods = new HashMap<>();
    final Map<String, List<SemanticType>> abstractMethods = new HashMap<>();
    final Map<String, EnumVariantInfo> enumValues = new HashMap<>();

    ClassInfo(String name, boolean nativeClass, boolean extendable, boolean abstractClass, List<SemanticType> typeParameters, ClassInfo superclass, List<ClassInfo> interfaces, SemanticType semanticType) {
        this.name = name;
        this.nativeClass = nativeClass;
        this.extendable = extendable;
        this.abstractClass = abstractClass;
        this.typeParameters = List.copyOf(typeParameters);
        this.superclass = superclass;
        this.interfaces = List.copyOf(interfaces);
        this.semanticType = semanticType;
    }

    boolean hasField(String fieldName) {
        return fields.containsKey(fieldName) || (superclass != null && superclass.hasField(fieldName));
    }

    boolean hasMethod(String methodName) {
        return !methodOverloads(methodName).isEmpty();
    }

    SemanticType fieldType(String fieldName) {
        SemanticType fieldType = fields.get(fieldName);
        if (fieldType != null) {
            return fieldType;
        }
        return superclass != null ? superclass.fieldType(fieldName) : null;
    }

    ClassInfo fieldOwner(String fieldName) {
        if (fields.containsKey(fieldName)) {
            return this;
        }
        return superclass != null ? superclass.fieldOwner(fieldName) : null;
    }

    boolean isFieldPrivate(String fieldName) {
        if (fields.containsKey(fieldName)) {
            return privateFields.getOrDefault(fieldName, false);
        }
        return superclass != null && superclass.isFieldPrivate(fieldName);
    }

    List<SemanticType> methodOverloads(String methodName) {
        List<SemanticType> ownMethods = methods.get(methodName);
        List<SemanticType> inheritedMethods = superclass != null ? superclass.methodOverloads(methodName) : List.of();

        if (ownMethods != null && !ownMethods.isEmpty()) {
            return ownMethods;
        }
        if (!inheritedMethods.isEmpty()) {
            return inheritedMethods;
        }

        List<SemanticType> interfaceMethods = new ArrayList<>();
        for (ClassInfo iface : interfaces) {
            for (SemanticType overload : iface.methodOverloads(methodName)) {
                if (interfaceMethods.stream().noneMatch(existing -> Resolver.sameSignature(existing, overload))) {
                    interfaceMethods.add(overload);
                }
            }
        }
        return interfaceMethods;
    }

    ClassInfo methodOwner(String methodName) {
        List<SemanticType> ownMethods = methods.get(methodName);
        if (ownMethods != null && !ownMethods.isEmpty()) {
            return this;
        }
        if (superclass != null) {
            ClassInfo owner = superclass.methodOwner(methodName);
            if (owner != null) {
                return owner;
            }
        }
        for (ClassInfo iface : interfaces) {
            if (!iface.methodOverloads(methodName).isEmpty()) {
                return iface;
            }
        }
        return null;
    }

    boolean isMethodPrivate(String methodName) {
        List<SemanticType> ownMethods = methods.get(methodName);
        if (ownMethods != null && !ownMethods.isEmpty()) {
            return privateMethods.getOrDefault(methodName, false);
        }
        return superclass != null && superclass.isMethodPrivate(methodName);
    }

    SemanticType methodType(String methodName) {
        List<SemanticType> overloads = methodOverloads(methodName);
        if (overloads.size() == 1) {
            return overloads.get(0);
        }
        return overloads.isEmpty() ? null : new SemanticType(Builtins.FUNCTION, false, semanticType.superclass(), null, List.of(), false);
    }

    List<String> unresolvedAbstractMethods() {
        Map<String, List<SemanticType>> required = new HashMap<>();
        if (superclass != null) {
            for (Map.Entry<String, List<SemanticType>> entry : superclass.collectUnresolvedAbstractSignatures().entrySet()) {
                required.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        for (ClassInfo iface : interfaces) {
            for (Map.Entry<String, List<SemanticType>> entry : iface.collectUnresolvedAbstractSignatures().entrySet()) {
                List<SemanticType> signatures = required.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
                for (SemanticType signature : entry.getValue()) {
                    if (signatures.stream().noneMatch(existing -> Resolver.sameSignature(existing, signature))) {
                        signatures.add(signature);
                    }
                }
            }
        }
        for (Map.Entry<String, List<SemanticType>> entry : abstractMethods.entrySet()) {
            List<SemanticType> signatures = required.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
            for (SemanticType signature : entry.getValue()) {
                if (signatures.stream().noneMatch(existing -> Resolver.sameSignature(existing, signature))) {
                    signatures.add(signature);
                }
            }
        }
        for (Map.Entry<String, List<SemanticType>> entry : concreteMethods.entrySet()) {
            List<SemanticType> signatures = required.get(entry.getKey());
            if (signatures == null || signatures.isEmpty()) {
                continue;
            }
            signatures.removeIf(requiredSignature -> entry.getValue().stream().anyMatch(
                    concreteSignature -> Resolver.satisfiesAbstractSignature(requiredSignature, concreteSignature)
            ));
            if (signatures.isEmpty()) {
                required.remove(entry.getKey());
            }
        }
        return required.keySet().stream().sorted().toList();
    }

    private Map<String, List<SemanticType>> collectUnresolvedAbstractSignatures() {
        Map<String, List<SemanticType>> required = new HashMap<>();
        if (superclass != null) {
            for (Map.Entry<String, List<SemanticType>> entry : superclass.collectUnresolvedAbstractSignatures().entrySet()) {
                required.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        for (ClassInfo iface : interfaces) {
            for (Map.Entry<String, List<SemanticType>> entry : iface.collectUnresolvedAbstractSignatures().entrySet()) {
                List<SemanticType> signatures = required.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
                for (SemanticType signature : entry.getValue()) {
                    if (signatures.stream().noneMatch(existing -> Resolver.sameSignature(existing, signature))) {
                        signatures.add(signature);
                    }
                }
            }
        }
        for (Map.Entry<String, List<SemanticType>> entry : abstractMethods.entrySet()) {
            List<SemanticType> signatures = required.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
            for (SemanticType signature : entry.getValue()) {
                if (signatures.stream().noneMatch(existing -> Resolver.sameSignature(existing, signature))) {
                    signatures.add(signature);
                }
            }
        }
        for (Map.Entry<String, List<SemanticType>> entry : concreteMethods.entrySet()) {
            List<SemanticType> signatures = required.get(entry.getKey());
            if (signatures == null || signatures.isEmpty()) {
                continue;
            }
            signatures.removeIf(requiredSignature -> entry.getValue().stream().anyMatch(
                    concreteSignature -> Resolver.satisfiesAbstractSignature(requiredSignature, concreteSignature)
            ));
            if (signatures.isEmpty()) {
                required.remove(entry.getKey());
            }
        }
        return required;
    }
}
