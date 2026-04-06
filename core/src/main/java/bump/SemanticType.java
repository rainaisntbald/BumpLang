package bump;

import java.util.List;

public class SemanticType {
    private final String name;
    private final boolean nativeClass;
    private final boolean extendable;
    private final SemanticType superclass;
    private final List<SemanticType> interfaces;
    private final SemanticType returnType;
    private final List<SemanticType> parameterTypes;
    private final List<SemanticType> typeParameters;
    private final List<SemanticType> typeArguments;
    private final boolean signatureKnown;
    private final boolean typeParameter;
    private final SemanticType upperBound;

    public SemanticType(String name, boolean nativeClass, SemanticType superclass) {
        this(name, nativeClass, false, superclass, List.of(), null, List.of(), List.of(), List.of(), false, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass) {
        this(name, nativeClass, extendable, superclass, List.of(), null, List.of(), List.of(), List.of(), false, false, null);
    }

    public SemanticType(String name, boolean nativeClass, SemanticType superclass, SemanticType returnType) {
        this(name, nativeClass, false, superclass, List.of(), returnType, List.of(), List.of(), List.of(), false, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, SemanticType returnType) {
        this(name, nativeClass, extendable, superclass, List.of(), returnType, List.of(), List.of(), List.of(), false, false, null);
    }

    public SemanticType(String name, boolean nativeClass, SemanticType superclass, SemanticType returnType, List<SemanticType> parameterTypes) {
        this(name, nativeClass, false, superclass, List.of(), returnType, parameterTypes, List.of(), List.of(), true, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, SemanticType returnType, List<SemanticType> parameterTypes) {
        this(name, nativeClass, extendable, superclass, List.of(), returnType, parameterTypes, List.of(), List.of(), true, false, null);
    }

    public SemanticType(String name, boolean nativeClass, SemanticType superclass, SemanticType returnType, List<SemanticType> parameterTypes, boolean signatureKnown) {
        this(name, nativeClass, false, superclass, List.of(), returnType, parameterTypes, List.of(), List.of(), signatureKnown, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, SemanticType returnType, List<SemanticType> parameterTypes, boolean signatureKnown) {
        this(name, nativeClass, extendable, superclass, List.of(), returnType, parameterTypes, List.of(), List.of(), signatureKnown, false, null);
    }

    public SemanticType(String name, boolean nativeClass, SemanticType superclass, SemanticType returnType, List<SemanticType> parameterTypes, List<SemanticType> typeParameters) {
        this(name, nativeClass, false, superclass, List.of(), returnType, parameterTypes, typeParameters, List.of(), true, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, List<SemanticType> interfaces, List<SemanticType> typeArguments) {
        this(name, nativeClass, extendable, superclass, interfaces, null, List.of(), List.of(), typeArguments, false, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, List<SemanticType> interfaces) {
        this(name, nativeClass, extendable, superclass, interfaces, null, List.of(), List.of(), List.of(), false, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, List<SemanticType> interfaces, SemanticType returnType, List<SemanticType> parameterTypes, boolean signatureKnown) {
        this(name, nativeClass, extendable, superclass, interfaces, returnType, parameterTypes, List.of(), List.of(), signatureKnown, false, null);
    }

    public SemanticType(String name, boolean nativeClass, boolean extendable, SemanticType superclass, List<SemanticType> interfaces, SemanticType returnType, List<SemanticType> parameterTypes, List<SemanticType> typeParameters, List<SemanticType> typeArguments, boolean signatureKnown, boolean typeParameter, SemanticType upperBound) {
        this.name = name;
        this.nativeClass = nativeClass;
        this.extendable = extendable;
        this.superclass = superclass;
        this.interfaces = List.copyOf(interfaces);
        this.returnType = returnType;
        this.parameterTypes = List.copyOf(parameterTypes);
        this.typeParameters = List.copyOf(typeParameters);
        this.typeArguments = List.copyOf(typeArguments);
        this.signatureKnown = signatureKnown;
        this.typeParameter = typeParameter;
        this.upperBound = upperBound;
    }

    public static SemanticType typeParameter(String name, SemanticType upperBound) {
        return new SemanticType(name, false, false, null, List.of(), null, List.of(), List.of(), List.of(), false, true, upperBound);
    }

    public String name() {
        return name;
    }

    public boolean isNativeClass() {
        return nativeClass;
    }

    public boolean isExtendable() {
        return extendable;
    }

    public SemanticType superclass() {
        return superclass;
    }

    public List<SemanticType> interfaces() {
        return interfaces;
    }

    public SemanticType returnType() {
        return returnType;
    }

    public List<SemanticType> parameterTypes() {
        return parameterTypes;
    }

    public List<SemanticType> typeParameters() {
        return typeParameters;
    }

    public List<SemanticType> typeArguments() {
        return typeArguments;
    }

    public boolean signatureKnown() {
        return signatureKnown;
    }

    public boolean isTypeParameter() {
        return typeParameter;
    }

    public SemanticType upperBound() {
        return upperBound;
    }

    public boolean isAssignableFrom(SemanticType other) {
        if (other == null) {
            return true;
        }
        if (Builtins.COMPARABLE.equals(name) && Builtins.OBJECT.equals(other.name)) {
            return true;
        }
        if (Builtins.FUNCTION.equals(name) && Builtins.FUNCTION.equals(other.name)) {
            return functionSignatureAssignableFrom(other);
        }
        if (typeParameter) {
            if (other.typeParameter && name.equals(other.name)) {
                return true;
            }
            if (upperBound == null) {
                return true;
            }
            return upperBound.isAssignableFrom(other);
        }
        if (other.typeParameter) {
            if (other.upperBound == null) {
                return true;
            }
            return isAssignableFrom(other.upperBound);
        }
        java.util.ArrayDeque<SemanticType> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<SemanticType> visited = new java.util.HashSet<>();
        queue.add(other);
        while (!queue.isEmpty()) {
            SemanticType current = queue.removeFirst();
            if (current == null || !visited.add(current)) {
                continue;
            }
            if (name.equals(current.name) && typeArgumentsMatch(current.typeArguments)) {
                return true;
            }
            if (current.superclass != null) {
                queue.addLast(current.superclass);
            }
            for (SemanticType iface : current.interfaces) {
                if (iface != null) {
                    queue.addLast(iface);
                }
            }
        }
        return false;
    }

    public String displayName() {
        if (Builtins.FUNCTION.equals(name) && signatureKnown && returnType != null) {
            java.util.ArrayList<String> parts = new java.util.ArrayList<>();
            parts.add(returnType.displayName());
            for (SemanticType parameterType : parameterTypes) {
                parts.add(parameterType.displayName());
            }
            return "Function<" + String.join(", ", parts) + ">";
        }
        if (typeArguments.isEmpty()) {
            return name;
        }
        return name + "<" + String.join(", ", typeArguments.stream().map(SemanticType::displayName).toList()) + ">";
    }

    public SemanticType withTypeArguments(List<SemanticType> args) {
        return new SemanticType(
                name,
                nativeClass,
                extendable,
                superclass,
                interfaces,
                returnType,
                parameterTypes,
                typeParameters,
                args,
                signatureKnown,
                typeParameter,
                upperBound
        );
    }

    private boolean typeArgumentsMatch(List<SemanticType> actualTypeArguments) {
        if (typeArguments.isEmpty()) {
            return true;
        }
        if (actualTypeArguments.isEmpty()) {
            return true;
        }
        if (actualTypeArguments.size() != typeArguments.size()) {
            return false;
        }
        for (int i = 0; i < typeArguments.size(); i++) {
            if (!sameConcreteType(typeArguments.get(i), actualTypeArguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean functionSignatureAssignableFrom(SemanticType other) {
        if (!signatureKnown) {
            return true;
        }
        if (!other.signatureKnown) {
            return true;
        }
        if (returnType == null || other.returnType == null) {
            return true;
        }
        if (!returnType.isAssignableFrom(other.returnType)) {
            return false;
        }
        if (parameterTypes.size() != other.parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            SemanticType expectedParameter = parameterTypes.get(i);
            SemanticType actualParameter = other.parameterTypes.get(i);
            if (expectedParameter == null || actualParameter == null) {
                continue;
            }
            if (!actualParameter.isAssignableFrom(expectedParameter)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameConcreteType(SemanticType left, SemanticType right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left.isTypeParameter() || right.isTypeParameter()) {
            if (left.isTypeParameter() != right.isTypeParameter()) {
                return false;
            }
            return left.name().equals(right.name());
        }
        if (!left.name().equals(right.name())) {
            return false;
        }
        if (left.typeArguments().size() != right.typeArguments().size()) {
            return false;
        }
        for (int i = 0; i < left.typeArguments().size(); i++) {
            if (!sameConcreteType(left.typeArguments().get(i), right.typeArguments().get(i))) {
                return false;
            }
        }
        return true;
    }
}
