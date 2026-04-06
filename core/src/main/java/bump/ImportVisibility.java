package bump;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ImportVisibility {
    private final Map<String, Set<String>> directDependencies = new HashMap<>();

    void addDependency(String fromFile, String toFile) {
        if (fromFile == null || toFile == null) {
            return;
        }
        directDependencies
                .computeIfAbsent(fromFile, ignored -> new HashSet<>())
                .add(toFile);
    }

    boolean canReference(String fromFile, String symbolFile) {
        if (fromFile == null || symbolFile == null) {
            return true;
        }
        if (fromFile.equals(symbolFile)) {
            return true;
        }
        return directDependencies.getOrDefault(fromFile, Collections.emptySet()).contains(symbolFile);
    }
}
