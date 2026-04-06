package bump;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourcePreprocessorTest {
    @Test
    void importsResolveFromEntryRootAcrossSiblingDirectories() throws Exception {
        Path root = Files.createTempDirectory("bump-import-root");
        Path firstDir = root.resolve("first_dir");
        Path secondDir = root.resolve("second_dir");
        Files.createDirectories(firstDir);
        Files.createDirectories(secondDir);

        Files.writeString(
                firstDir.resolve("a.bump"),
                """
                fun Integer helper() {
                    return 7;
                }
                """
        );
        Files.writeString(
                secondDir.resolve("b.bump"),
                """
                import first_dir.a;
                print(helper());
                """
        );
        Path entry = root.resolve("main.bump");
        Files.writeString(
                entry,
                """
                import second_dir.b;
                """
        );

        synchronized (SourcePreprocessorTest.class) {
            PrintStream originalOut = System.out;
            ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
            PrintStream capture = new PrintStream(outputBuffer, true, StandardCharsets.UTF_8);
            try {
                System.setOut(capture);
                new ProgramRunner().runPath(entry.toString());
            } finally {
                System.setOut(originalOut);
                capture.close();
            }
            assertEquals("7", outputBuffer.toString(StandardCharsets.UTF_8).trim());
        }
    }
}
