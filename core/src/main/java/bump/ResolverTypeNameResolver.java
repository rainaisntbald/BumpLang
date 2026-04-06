package bump;

import java.util.ArrayList;
import java.util.List;

final class ResolverTypeNameResolver {
    private final Resolver resolver;

    ResolverTypeNameResolver(Resolver resolver) {
        this.resolver = resolver;
    }

    SemanticType resolveTypeName(String typeName, String message) {
        ParsedTypeName parsedType = ParsedTypeName.parse(typeName);
        return resolveParsedTypeName(parsedType, message);
    }

    private SemanticType resolveParsedTypeName(ParsedTypeName parsedType, String message) {
        if (Builtins.FUNCTION.equals(parsedType.name()) && !parsedType.typeArguments().isEmpty()) {
            return resolveFunctionType(parsedType, message);
        }
        if (parsedType.typeArguments().isEmpty()) {
            SemanticType typeParameter = resolver.lookupTypeParameter(parsedType.name());
            if (typeParameter != null) {
                return typeParameter;
            }
            return resolver.resolveDeclaredType(parsedType.name(), message);
        }
        SemanticType typeParameter = resolver.lookupTypeParameter(parsedType.name());
        if (typeParameter != null) {
            throw BumpException.semantic("Type parameter '" + parsedType.name() + "' cannot have type arguments.");
        }
        SemanticType base = resolver.resolveDeclaredType(parsedType.name(), message);
        ClassInfo classInfo = resolver.classInfoForType(base);
        if (classInfo == null) {
            throw BumpException.semantic(message);
        }
        if (classInfo.typeParameters.size() != parsedType.typeArguments().size()) {
            throw BumpException.semantic("Type '" + parsedType.name() + "' expects " + classInfo.typeParameters.size()
                    + " type argument(s), but got " + parsedType.typeArguments().size() + ".");
        }
        List<SemanticType> resolvedTypeArguments = new ArrayList<>();
        for (int i = 0; i < parsedType.typeArguments().size(); i++) {
            SemanticType resolvedArgument = resolveParsedTypeName(parsedType.typeArguments().get(i), message);
            SemanticType expectedBound = classInfo.typeParameters.get(i).upperBound();
            if (expectedBound != null && !expectedBound.isAssignableFrom(resolvedArgument)) {
                throw BumpException.semantic("Type argument " + resolvedArgument.displayName() + " does not satisfy bound "
                        + expectedBound.displayName() + " for '" + parsedType.name() + "'.");
            }
            resolvedTypeArguments.add(resolvedArgument);
        }
        return base.withTypeArguments(resolvedTypeArguments);
    }

    private SemanticType resolveFunctionType(ParsedTypeName parsedType, String message) {
        if (parsedType.typeArguments().isEmpty()) {
            return resolver.resolveDeclaredType(Builtins.FUNCTION, message);
        }
        SemanticType returnType = resolveParsedTypeName(parsedType.typeArguments().get(0), message);
        List<SemanticType> parameterTypes = new ArrayList<>();
        for (int i = 1; i < parsedType.typeArguments().size(); i++) {
            parameterTypes.add(resolveParsedTypeName(parsedType.typeArguments().get(i), message));
        }
        return new SemanticType(Builtins.FUNCTION, false, resolver.functionType, returnType, parameterTypes, true);
    }
}
