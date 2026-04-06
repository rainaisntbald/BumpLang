package bump;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ResolverTypeSystem {
    private final Resolver resolver;

    ResolverTypeSystem(Resolver resolver) {
        this.resolver = resolver;
    }

    SemanticType instantiateGenericOverload(SemanticType overload, List<SemanticType> argumentTypes) {
        if (overload.typeParameters().isEmpty()) {
            return overload;
        }
        if (argumentTypes.size() != overload.parameterTypes().size()) {
            return null;
        }
        Map<String, SemanticType> bindings = new HashMap<>();
        List<SemanticType> instantiatedParameters = new ArrayList<>();
        for (int i = 0; i < overload.parameterTypes().size(); i++) {
            SemanticType parameterType = overload.parameterTypes().get(i);
            SemanticType argumentType = argumentTypes.get(i);
            SemanticType instantiated = instantiateType(parameterType, argumentType, bindings);
            if (instantiated == null) {
                return null;
            }
            instantiatedParameters.add(instantiated);
        }
        SemanticType instantiatedReturnType = instantiateType(overload.returnType(), null, bindings);
        if (instantiatedReturnType != null && instantiatedReturnType.isTypeParameter()) {
            instantiatedReturnType = instantiatedReturnType.upperBound();
        }
        return new SemanticType(
                Builtins.FUNCTION,
                false,
                resolver.functionType,
                instantiatedReturnType,
                instantiatedParameters
        );
    }

    SemanticType instantiateOverloadWithExplicitTypeArguments(
            SemanticType overload,
            List<SemanticType> explicitTypeArguments
    ) {
        if (explicitTypeArguments.isEmpty()) {
            return overload;
        }
        List<SemanticType> declaredTypeParameters = overload.typeParameters();
        if (declaredTypeParameters.isEmpty()) {
            return null;
        }
        if (declaredTypeParameters.size() != explicitTypeArguments.size()) {
            return null;
        }

        Map<String, SemanticType> bindings = new HashMap<>();
        for (int i = 0; i < declaredTypeParameters.size(); i++) {
            SemanticType typeParameter = declaredTypeParameters.get(i);
            SemanticType explicitType = explicitTypeArguments.get(i);
            SemanticType upperBound = typeParameter.upperBound() != null ? typeParameter.upperBound() : resolver.objectType;
            if (!upperBound.isAssignableFrom(explicitType)) {
                return null;
            }
            bindings.put(typeParameter.name(), explicitType);
        }

        SemanticType returnType = applyTypeArguments(overload.returnType(), bindings, Set.of());
        List<SemanticType> parameterTypes = overload.parameterTypes().stream()
                .map(parameterType -> applyTypeArguments(parameterType, bindings, Set.of()))
                .toList();
        return new SemanticType(Builtins.FUNCTION, false, resolver.functionType, returnType, parameterTypes);
    }

    SemanticType applyClassTypeArguments(ClassInfo classInfo, SemanticType receiverType, SemanticType signature) {
        Map<String, SemanticType> bindings = classTypeBindings(classInfo, receiverType);
        if (bindings.isEmpty()) {
            return signature;
        }
        Set<String> shadowed = new HashSet<>();
        List<SemanticType> remainingTypeParameters = new ArrayList<>();
        for (SemanticType methodTypeParameter : signature.typeParameters()) {
            String name = methodTypeParameter.name();
            SemanticType receiverBinding = bindings.get(name);
            if (receiverBinding == null) {
                shadowed.add(name);
                remainingTypeParameters.add(methodTypeParameter);
                continue;
            }
            SemanticType requiredBound = methodTypeParameter.upperBound() != null ? methodTypeParameter.upperBound() : resolver.objectType;
            if (!requiredBound.isAssignableFrom(receiverBinding)) {
                return null;
            }
        }
        SemanticType returnType = applyTypeArguments(signature.returnType(), bindings, shadowed);
        List<SemanticType> parameterTypes = signature.parameterTypes().stream()
                .map(parameterType -> applyTypeArguments(parameterType, bindings, shadowed))
                .toList();
        return new SemanticType(
                Builtins.FUNCTION,
                false,
                resolver.functionType,
                returnType,
                parameterTypes,
                remainingTypeParameters
        );
    }

    Map<String, SemanticType> classTypeBindings(ClassInfo classInfo, SemanticType receiverType) {
        if (classInfo.typeParameters.isEmpty()) {
            return Map.of();
        }
        List<SemanticType> receiverTypeArguments = receiverType != null ? receiverType.typeArguments() : List.of();
        Map<String, SemanticType> bindings = new HashMap<>();
        for (int i = 0; i < classInfo.typeParameters.size(); i++) {
            SemanticType classTypeParameter = classInfo.typeParameters.get(i);
            SemanticType bound = classTypeParameter.upperBound() != null ? classTypeParameter.upperBound() : resolver.objectType;
            SemanticType mapped = i < receiverTypeArguments.size() ? receiverTypeArguments.get(i) : bound;
            bindings.put(classTypeParameter.name(), mapped);
        }
        return bindings;
    }

    SemanticType applyTypeArguments(SemanticType input, Map<String, SemanticType> bindings, Set<String> shadowedTypeParameters) {
        if (input == null) {
            return null;
        }
        if (input.isTypeParameter()) {
            if (shadowedTypeParameters.contains(input.name())) {
                return input;
            }
            return bindings.getOrDefault(input.name(), input);
        }
        if (Builtins.FUNCTION.equals(input.name()) && input.signatureKnown()) {
            SemanticType resolvedReturnType = applyTypeArguments(input.returnType(), bindings, shadowedTypeParameters);
            List<SemanticType> resolvedParameterTypes = input.parameterTypes().stream()
                    .map(parameterType -> applyTypeArguments(parameterType, bindings, shadowedTypeParameters))
                    .toList();
            return new SemanticType(
                    Builtins.FUNCTION,
                    false,
                    resolver.functionType,
                    resolvedReturnType,
                    resolvedParameterTypes,
                    input.typeParameters()
            );
        }
        if (input.typeArguments().isEmpty()) {
            return input;
        }
        List<SemanticType> resolvedTypeArguments = input.typeArguments().stream()
                .map(typeArgument -> applyTypeArguments(typeArgument, bindings, shadowedTypeParameters))
                .toList();
        return input.withTypeArguments(resolvedTypeArguments);
    }

    int typeDistance(SemanticType actual, SemanticType expected) {
        if (expected == null || actual == null || actual == resolver.nullType) {
            return 100;
        }
        ArrayDeque<SemanticType> queue = new ArrayDeque<>();
        ArrayDeque<Integer> distances = new ArrayDeque<>();
        HashSet<SemanticType> visited = new HashSet<>();
        queue.add(actual);
        distances.add(0);
        while (!queue.isEmpty()) {
            SemanticType current = queue.removeFirst();
            int distance = distances.removeFirst();
            if (current == null || !visited.add(current)) {
                continue;
            }
            if (expected.isAssignableFrom(current)) {
                return distance;
            }
            if (current.superclass() != null) {
                queue.addLast(current.superclass());
                distances.addLast(distance + 1);
            }
            for (SemanticType iface : current.interfaces()) {
                if (iface != null) {
                    queue.addLast(iface);
                    distances.addLast(distance + 1);
                }
            }
        }
        return -1;
    }

    private SemanticType instantiateType(SemanticType declaredType, SemanticType argumentType, Map<String, SemanticType> bindings) {
        if (declaredType == null) {
            return null;
        }
        if (Builtins.FUNCTION.equals(declaredType.name()) && declaredType.signatureKnown()) {
            SemanticType argumentReturnType = null;
            List<SemanticType> argumentParameterTypes = List.of();
            if (argumentType != null
                    && Builtins.FUNCTION.equals(argumentType.name())
                    && argumentType.signatureKnown()
                    && declaredType.parameterTypes().size() == argumentType.parameterTypes().size()) {
                argumentReturnType = argumentType.returnType();
                argumentParameterTypes = argumentType.parameterTypes();
            }
            SemanticType instantiatedReturnType = instantiateType(declaredType.returnType(), argumentReturnType, bindings);
            if (instantiatedReturnType == null && declaredType.returnType() != null) {
                return null;
            }
            List<SemanticType> instantiatedParameterTypes = new ArrayList<>();
            for (int i = 0; i < declaredType.parameterTypes().size(); i++) {
                SemanticType declaredParameter = declaredType.parameterTypes().get(i);
                SemanticType argumentParameter = i < argumentParameterTypes.size() ? argumentParameterTypes.get(i) : null;
                SemanticType instantiatedParameter = instantiateType(declaredParameter, argumentParameter, bindings);
                if (instantiatedParameter == null && declaredParameter != null) {
                    return null;
                }
                instantiatedParameterTypes.add(instantiatedParameter);
            }
            return new SemanticType(
                    Builtins.FUNCTION,
                    false,
                    resolver.functionType,
                    instantiatedReturnType,
                    instantiatedParameterTypes
            );
        }
        if (!declaredType.typeArguments().isEmpty()) {
            List<SemanticType> argumentTypeArguments = List.of();
            if (argumentType != null
                    && declaredType.name().equals(argumentType.name())
                    && declaredType.typeArguments().size() == argumentType.typeArguments().size()) {
                argumentTypeArguments = argumentType.typeArguments();
            }
            List<SemanticType> resolvedArguments = new ArrayList<>();
            for (int i = 0; i < declaredType.typeArguments().size(); i++) {
                SemanticType declaredArgument = declaredType.typeArguments().get(i);
                SemanticType providedArgument = i < argumentTypeArguments.size() ? argumentTypeArguments.get(i) : null;
                SemanticType resolved = instantiateType(declaredArgument, providedArgument, bindings);
                if (resolved == null && declaredArgument != null) {
                    return null;
                }
                resolvedArguments.add(resolved);
            }
            return declaredType.withTypeArguments(resolvedArguments);
        }
        if (!declaredType.isTypeParameter()) {
            return declaredType;
        }
        SemanticType upperBound = declaredType.upperBound() != null ? declaredType.upperBound() : resolver.objectType;
        SemanticType existing = bindings.get(declaredType.name());
        if (argumentType == null || argumentType == resolver.nullType) {
            if (existing != null) {
                return existing;
            }
            bindings.put(declaredType.name(), upperBound);
            return upperBound;
        }
        if (!upperBound.isAssignableFrom(argumentType)) {
            return null;
        }
        if (existing == null) {
            bindings.put(declaredType.name(), argumentType);
            return argumentType;
        }
        SemanticType merged = mergeTypeBindings(existing, argumentType);
        if (merged == null || !upperBound.isAssignableFrom(merged)) {
            return null;
        }
        bindings.put(declaredType.name(), merged);
        return merged;
    }

    private SemanticType mergeTypeBindings(SemanticType existing, SemanticType candidate) {
        if (existing == null) {
            return candidate;
        }
        if (candidate == null) {
            return existing;
        }
        if (existing.isAssignableFrom(candidate)) {
            return existing;
        }
        if (candidate.isAssignableFrom(existing)) {
            return candidate;
        }
        return null;
    }
}
