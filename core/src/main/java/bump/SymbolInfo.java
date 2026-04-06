package bump;

import java.util.ArrayList;
import java.util.List;

record SymbolInfo(SymbolKind kind, SemanticType semanticType, ClassInfo classInfo, List<SemanticType> overloads, String sourceFile) {
    SymbolInfo withOverload(SemanticType overload) {
        List<SemanticType> updated = new ArrayList<>(overloads);
        updated.add(overload);
        SemanticType displayType = updated.size() == 1
                ? updated.get(0)
                : new SemanticType(Builtins.FUNCTION, false, overload.superclass(), null, List.of(), false);
        return new SymbolInfo(SymbolKind.FUNCTION, displayType, null, List.copyOf(updated), sourceFile);
    }
}
