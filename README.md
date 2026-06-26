# BumpLang


BumpLang is a custom interpreted programming language built from scratch in Java to explore interpreter design and language implementation.
<br>
<br>
This project implements the core components of a language toolchain, including lexical analysis, parsing, AST construction, interpretation, runtime object management, module resolution, and developer tooling.

## Demo
<img height="399" alt="image" src="https://github.com/user-attachments/assets/58440eea-786f-4cca-94bb-7446e83f8e07" /><br>
<sup>BumpLang running with syntax highlighting and LSP diagnostics</sup>


## Highlights

- Hand-written lexer and recursive descent parser.
- Custom AST representation with visitor-based interpretation.
- Lexical scoping and runtime object system.
- Classes, inheritance, interfaces, and abstract classes.
- Module loading and standard library support.
- LSP integration for editor diagnostics.
- Java interoperability layer.

## Key Architecture
- **Lexer** - Custom lexer with regex-based token recognition and source location tracking for diagnostics.
- **Parser** - Recursive descent parser with precedence handling for expressions and syntax constructs.
  - Some syntax constructs, such as enhanced for loops, are lowered into the AST in a simplified form.
- **Abstract Syntax Tree** - AST used to encode the program and guide the interpreter's execution
- **Tree-walk Interpreter** - Executes AST nodes using a visitor pattern with lexical environments and runtime object representations.
- **Language Server Protocol** - Provides an LSP which shows diagnostic information (warnings/errors) in real-time to developers.

## Key Features
- **Basic Language Features** - Provides variables, built-in types and literal forms, control flow, iteration, and exception handling.
- **Object-oriented Patterns** - Allows for classes, instances, encapsulation, inheritance, interface-style contracts, and abstract classes.
- **Module System** - Allows for basic linking of multiple files together using `import` statements, along with an included standard library.
- **First-class Functions** - Functions can be treated as objects, and can be passed into other functions as such.
- **Java Interoperability** - Allows the language to interact with Java through a custom Java class environment running on the interpreter level, allowing for the language to be extended past the current standard library.
- **Developer Tooling** - Includes Language Server Protocol support for editor diagnostics and an improved developer experience.

## Example

```
class Person {
    String name;

    fun void init(String name) {
        this.name = name;
    }
}

Person person = Person("Alice");
print("Hello, " + person.name);
```

## Documentation

- `LANGUAGE_SPEC.md`: Language features, semantics, and developer examples

## Repository Layout

- `core/`       Language implementation, including lexer, parser, AST, interpreter, runtime, and stdlib
- `bump-lsp/`   Language Server Protocol implementation using LSP4J

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
