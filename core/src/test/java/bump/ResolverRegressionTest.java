package bump;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResolverRegressionTest {
    @Test
    void genericForeachOperatorDoesNotCrashResolver() {
        String program = """
                import stdlib.collections;
                import stdlib.iterable;

                HashMap<Integer, Integer> freq = HashMap<Integer, Integer>();
                freq.set(1, 2);

                Integer total = 0;
                for (Entry<Integer, Integer> bucket : freq) {
                    total = total + (bucket.key * bucket.value);
                }
                print(total);
                """;

        SemanticTestHarness.RunResult result = SemanticTestHarness.run(program, SemanticTestHarness.RunMode.WITH_IMPORT_DIRECTIVES);
        assertNull(result.error(), () -> "Unexpected resolver/runtime error: " + result.error());
        assertEquals("2", SemanticTestHarness.normalize(result.output()));
    }
}
