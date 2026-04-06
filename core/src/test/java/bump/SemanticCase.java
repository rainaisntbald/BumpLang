package bump;

import java.util.Set;

record SemanticCase(
        String name,
        SemanticTestHarness.RunMode mode,
        String program,
        String expectedOutput,
        String expectedErrorPart,
        Set<Integer> specSections
) {
    static SemanticCase success(String name, SemanticTestHarness.RunMode mode, String program, String expectedOutput, Set<Integer> sections) {
        return new SemanticCase(name, mode, program, expectedOutput, null, sections);
    }

    static SemanticCase failure(String name, SemanticTestHarness.RunMode mode, String program, String expectedErrorPart, Set<Integer> sections) {
        return new SemanticCase(name, mode, program, null, expectedErrorPart, sections);
    }
}
