package bump;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticLanguageSpecTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("bump.SemanticLanguageSpecCases#successCases")
    void successCasesRun(SemanticCase testCase) {
        SemanticTestHarness.RunResult result = SemanticTestHarness.run(testCase.program(), testCase.mode());
        assertNull(result.error(), () -> "Unexpected error in " + testCase.name() + ": "
                + (result.error() == null ? "" : result.error().getMessage()));
        assertEquals(
                SemanticTestHarness.normalize(testCase.expectedOutput()),
                SemanticTestHarness.normalize(result.output()),
                () -> "Output mismatch in " + testCase.name()
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bump.SemanticLanguageSpecCases#failureCases")
    void failureCasesRun(SemanticCase testCase) {
        SemanticTestHarness.RunResult result = SemanticTestHarness.run(testCase.program(), testCase.mode());
        assertNotNull(result.error(), () -> "Expected failure in " + testCase.name() + " but program succeeded.");
        assertTrue(
                result.error().getMessage().contains(testCase.expectedErrorPart()),
                () -> "Expected error containing '" + testCase.expectedErrorPart() + "' but got '" + result.error().getMessage() + "'"
        );
    }

    @Test
    void everyLanguageSpecSectionIsCoveredBySemanticTests() {
        var covered = SemanticLanguageSpecCases.coveredSections();
        SemanticLanguageSpecCases.requiredSections().forEach(section ->
                assertTrue(covered.contains(section), () -> "Missing semantic test coverage for LANGUAGE_SPEC section " + section));
    }
}
