package bump;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class SourcePreprocessor {
    record PreparedSource(String source, List<BumpException.SourceLocation> locations, ImportVisibility visibility) {}

    private record PreprocessorDirective(String specifier) {}

    private static final String STDLIB_IMPORT_PREFIX = "stdlib.";
    private static final Pattern MODULE_SEGMENT_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    private static final String STDLIB_ANCHOR_FILE = "iterable.bump";
    private static final Path DEFAULT_STDLIB_DIRECTORY = Path.of("core", "src", "main", "resources", "stdlib").toAbsolutePath().normalize();
    private static final Path CLASSPATH_STDLIB_DIRECTORY = Path.of(".bump-embedded-stdlib").toAbsolutePath().normalize();
    private static final String CLASSPATH_STDLIB_RESOURCE_ROOT = "stdlib/";

    private final Lexer lexer = new Lexer();

    PreparedSource preparePath(Path scriptPath) throws IOException {
        Path projectRoot = scriptPath.getParent();
        if (projectRoot == null) {
            projectRoot = Path.of(".").toAbsolutePath().normalize();
        }
        Set<Path> included = new HashSet<>();
        ArrayDeque<Path> stack = new ArrayDeque<>();
        SourceComposer composer = new SourceComposer();
        ImportVisibility visibility = new ImportVisibility();
        appendFile(scriptPath, projectRoot, composer, included, stack, visibility);
        return new PreparedSource(composer.source(), composer.locations(), visibility);
    }

    PreparedSource prepareSource(String source, Path baseDirectory) {
        try {
            Path projectRoot = baseDirectory.toAbsolutePath().normalize();
            Set<Path> included = new HashSet<>();
            ArrayDeque<Path> stack = new ArrayDeque<>();
            SourceComposer composer = new SourceComposer();
            ImportVisibility visibility = new ImportVisibility();
            appendSource(source, projectRoot, "<source>", composer, included, stack, visibility);
            return new PreparedSource(composer.source(), composer.locations(), visibility);
        } catch (IOException error) {
            throw BumpException.runtime("I/O error while loading imports: " + error.getMessage());
        }
    }

    private void appendFile(
            Path filePath,
            Path projectRoot,
            SourceComposer composer,
            Set<Path> included,
            ArrayDeque<Path> stack,
            ImportVisibility visibility
    ) throws IOException {
        Path normalized = filePath.normalize();
        String displayPath = displayPath(normalized);
        if (stack.contains(normalized)) {
            throw BumpException.runtime("Cyclic import detected involving '" + displayPath + "'.");
        }
        if (included.contains(normalized)) {
            return;
        }
        if (!pathExists(normalized)) {
            throw BumpException.runtime("Imported module file not found: '" + displayPath + "'.");
        }

        included.add(normalized);
        stack.push(normalized);
        try {
            String source = readSource(normalized);
            appendSource(source, projectRoot, displayPath, composer, included, stack, visibility);
        } finally {
            stack.pop();
        }
    }

    private void appendSource(
            String source,
            Path projectRoot,
            String label,
            SourceComposer composer,
            Set<Path> included,
            ArrayDeque<Path> stack,
            ImportVisibility visibility
    ) throws IOException {
        composer.appendUnmapped("// begin " + label + '\n');
        Map<Integer, PreprocessorDirective> directivesByLine = collectPreprocessorDirectives(source);

        String[] lines = source.split("\n", -1);
        boolean endsWithNewline = source.endsWith("\n");
        int lastIndex = lines.length - 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean hasTerminator = i < lastIndex || endsWithNewline;
            PreprocessorDirective directive = directivesByLine.get(i + 1);
            if (directive != null) {
                Path resolved = resolveImport(projectRoot, directive.specifier);
                visibility.addDependency(label, displayPath(resolved));
                appendFile(resolved, projectRoot, composer, included, stack, visibility);
                continue;
            }

            String renderedLine = hasTerminator ? line + "\n" : line;
            composer.appendMapped(renderedLine, label, i + 1);
        }

        if (!endsWithNewline) {
            composer.appendUnmapped("\n");
        }
        composer.appendUnmapped("// end " + label + '\n');
    }

    private Map<Integer, PreprocessorDirective> collectPreprocessorDirectives(String source) {
        Map<Integer, List<Token>> tokensByLine = new HashMap<>();
        for (Token token : lexer.tokenize(source)) {
            tokensByLine.computeIfAbsent(token.getLine(), ignored -> new ArrayList<>()).add(token);
        }

        Map<Integer, PreprocessorDirective> directives = new HashMap<>();
        for (Map.Entry<Integer, List<Token>> entry : tokensByLine.entrySet()) {
            List<Token> lineTokens = entry.getValue();
            if (lineTokens.isEmpty()) {
                continue;
            }
            Token first = lineTokens.get(0);
            if (first.getType() != TokenType.ID) {
                continue;
            }
            if ("include".equals(first.getText())) {
                throw BumpException.runtime(first, "The 'include' directive has been removed. Use 'import module.path;'.");
            }
            if (!"import".equals(first.getText())) {
                continue;
            }

            String moduleName = parseImportModuleSpecifier(lineTokens);
            if (moduleName == null) {
                throw BumpException.runtime(first, "Invalid import directive. Use: import stdlib.arrayList;");
            }
            directives.put(entry.getKey(), new PreprocessorDirective(moduleName));
        }
        return directives;
    }

    private String parseImportModuleSpecifier(List<Token> lineTokens) {
        int size = lineTokens.size();
        if (size == 0) {
            return null;
        }
        int end = size;
        if (lineTokens.get(end - 1).getType() == TokenType.SEMICOLON) {
            end--;
        } else {
            return null;
        }
        if (end <= 1) {
            return null;
        }

        Token second = lineTokens.get(1);
        if (second.getType() == TokenType.STRING && end == 2) {
            String moduleName = second.getText();
            validateImportModuleName(moduleName);
            return moduleName;
        }

        StringBuilder moduleBuilder = new StringBuilder();
        boolean expectSegment = true;
        for (int i = 1; i < end; i++) {
            Token token = lineTokens.get(i);
            if (expectSegment) {
                if (token.getType() != TokenType.ID) {
                    return null;
                }
                if (!MODULE_SEGMENT_PATTERN.matcher(token.getText()).matches()) {
                    return null;
                }
                if (!moduleBuilder.isEmpty()) {
                    moduleBuilder.append('.');
                }
                moduleBuilder.append(token.getText());
                expectSegment = false;
            } else {
                if (token.getType() != TokenType.DOT) {
                    return null;
                }
                expectSegment = true;
            }
        }
        if (expectSegment) {
            return null;
        }
        String moduleName = moduleBuilder.toString();
        validateImportModuleName(moduleName);
        return moduleName;
    }

    private Path resolveImport(Path projectRoot, String moduleName) {
        validateImportModuleName(moduleName);
        if (moduleName.startsWith(STDLIB_IMPORT_PREFIX)) {
            String modulePath = moduleName.substring(STDLIB_IMPORT_PREFIX.length()).replace('.', '/');
            if (modulePath.isEmpty()) {
                throw BumpException.runtime("Invalid stdlib import '" + moduleName + "'.");
            }
            return resolveStdlibPath(projectRoot, Path.of(modulePath + ".bump"));
        }
        String modulePath = moduleName.replace('.', '/');
        return projectRoot.resolve(modulePath + ".bump").normalize();
    }

    private Path resolveStdlibPath(Path baseDirectory, Path relativePath) {
        Path stdlibDirectory = resolveStdlibDirectory(baseDirectory);
        Path filesystemPath = stdlibDirectory.resolve(relativePath).normalize();
        if (Files.exists(filesystemPath)) {
            return filesystemPath;
        }
        return CLASSPATH_STDLIB_DIRECTORY.resolve(relativePath).normalize();
    }

    private Path resolveStdlibDirectory(Path baseDirectory) {
        for (Path current = baseDirectory; current != null; current = current.getParent()) {
            Path candidate = current.resolve("stdlib").normalize();
            if (Files.exists(candidate.resolve(STDLIB_ANCHOR_FILE))) {
                return candidate;
            }
            Path coreResourceCandidate = current.resolve("core").resolve("src").resolve("main").resolve("resources").resolve("stdlib").normalize();
            if (Files.exists(coreResourceCandidate.resolve(STDLIB_ANCHOR_FILE))) {
                return coreResourceCandidate;
            }
        }
        return DEFAULT_STDLIB_DIRECTORY;
    }

    private boolean pathExists(Path path) {
        if (Files.exists(path)) {
            return true;
        }
        if (!isClasspathStdlibPath(path)) {
            return false;
        }
        return openClasspathStdlib(path) != null;
    }

    private String readSource(Path path) throws IOException {
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        InputStream resource = openClasspathStdlib(path);
        if (resource == null) {
            throw new IOException("Imported module file not found: '" + displayPath(path) + "'.");
        }
        try (resource) {
            byte[] bytes = resource.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private InputStream openClasspathStdlib(Path path) {
        if (!isClasspathStdlibPath(path)) {
            return null;
        }
        Path relative = CLASSPATH_STDLIB_DIRECTORY.relativize(path.normalize());
        String resourcePath = CLASSPATH_STDLIB_RESOURCE_ROOT + relative.toString().replace('\\', '/');
        return SourcePreprocessor.class.getClassLoader().getResourceAsStream(resourcePath);
    }

    private boolean isClasspathStdlibPath(Path path) {
        return path.normalize().startsWith(CLASSPATH_STDLIB_DIRECTORY);
    }

    private String displayPath(Path path) {
        Path normalized = path.normalize();
        if (isClasspathStdlibPath(normalized)) {
            Path relative = CLASSPATH_STDLIB_DIRECTORY.relativize(normalized);
            return "stdlib/" + relative.toString().replace('\\', '/');
        }
        return normalized.toString();
    }

    private void validateImportModuleName(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            throw BumpException.runtime("Import module name cannot be empty.");
        }
        String[] segments = moduleName.split("\\.");
        if (segments.length < 2) {
            throw BumpException.runtime("Import module '" + moduleName + "' must use dotted module format like 'stdlib.arrayList'.");
        }
        for (String segment : segments) {
            if (!MODULE_SEGMENT_PATTERN.matcher(segment).matches()) {
                throw BumpException.runtime("Invalid import module '" + moduleName + "'. Use dotted identifiers like 'stdlib.arrayList'.");
            }
        }
    }

    private static final class SourceComposer {
        private final StringBuilder source = new StringBuilder();
        private final List<BumpException.SourceLocation> locations = new ArrayList<>();
        private int line = 1;

        void appendMapped(String text, String file, Integer fileLine) {
            append(text, file, fileLine);
        }

        void appendUnmapped(String text) {
            append(text, null, null);
        }

        String source() {
            return source.toString();
        }

        List<BumpException.SourceLocation> locations() {
            return Collections.unmodifiableList(new ArrayList<>(locations));
        }

        private void append(String text, String file, Integer fileLine) {
            int fileLineOffset = 0;
            for (int i = 0; i < text.length(); i++) {
                ensureLineSlot(line);
                if (file != null && fileLine != null && locations.get(line - 1) == null) {
                    locations.set(line - 1, new BumpException.SourceLocation(file, fileLine + fileLineOffset));
                }
                char ch = text.charAt(i);
                source.append(ch);
                if (ch == '\n') {
                    line++;
                    fileLineOffset++;
                }
            }
        }

        private void ensureLineSlot(int targetLine) {
            while (locations.size() < targetLine) {
                locations.add(null);
            }
        }
    }
}
