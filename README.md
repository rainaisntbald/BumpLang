# BumpLang

BumpLang is a hobby project I put far too much time into over the past month and a bit. It is a Java-based interpreted programming language, complete with an LSP.
<br>
<br>
No guarantees are made about the language's stability, performance, and maintenance, except that they're all awful.
<br>
<br>
The project's first commit is massive, since it was supposed to be a small exercise, and I got carried away with adding things... whoops.
<br>
<br>
Run at the risk of your own sanity

## Documentation

- `LANGUAGE_SPEC.md`: language features, semantics, and developer examples

## Repository Layout

- `core`: language core, parser/interpreter, runtime, and bundled stdlib files
- `bump-lsp`: language server implementation built using LSP4J

## Build

```bash
mvn clean package
```

## Run The Language

Run a file:

```bash
java -cp core/target/bump-core-0.1.0.jar bump.Main path/to/file.bump
```

Run the default sample:

```bash
java -cp core/target/bump-core-0.1.0.jar bump.Main
```

## Run The Language Server

```bash
java -jar bump-lsp/target/bump-lsp-0.1.0.jar
```

Disable `unsafe_java` warning diagnostics:

```bash
java -jar bump-lsp/target/bump-lsp-0.1.0.jar --no-unsafe-java-warnings
```

## Test

```bash
mvn test
```

---
