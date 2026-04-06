# BumpLang Language Specification
## 1. Program Structure

- A program is a sequence of statements.
- Statements end with `;` unless they are block forms (`if`, `while`, `for`, `try`, `switch`, `class`, `enum`, `extend`).
- Blocks use `{ ... }`.
- Type names are explicit in declarations.

Example:

```bump
Integer count = 0;
if (count == 0) {
    print("empty");
}
```

## 2. Imports

BumpLang supports source-level module directives:

- `import module.path;`

`import` resolves module-style names to files (`a.b.c` -> `a/b/c.bump`).  
Non-stdlib imports are resolved from the entry file's directory (project root for that run).  
Standard library modules use the `stdlib.` prefix:

```bump
import stdlib.arrayList;
import stdlib.option;
```

## 3. Built-in Types and Values

Core built-in classes:

- `Object`, `Class`, `Function`
- `Null`, `Bool`, `Integer`, `Float`, `Char`, `String`
- `Array`, `Map`, `File`
- `Exception` and subclasses like `TypeError`, `NameError`, `ValueError`, `IndexError`

Literal forms:

- Integer: `123`
- Float: `3.14`
- String: `"hello"`
- Bool: `true`, `false`
- Null: `null`
- Array: `[1, 2, 3]`
- Map: `{"name": "Ada", "score": 42}`

## 4. Variables and Assignment

Variables are declared with a type and name:

```bump
Integer hp = 10;
String title = "Rookie";
```

Assignment operators:

- `=`
- `+=`, `-=`, `*=`, `/=`
- `++`, `--` (prefix and postfix)

Step operator behavior:

- Prefix (`++x`, `--x`) updates first, then yields the updated value.
- Postfix (`x++`, `x--`) yields the current value, then updates.

## 5. Expressions and Operators

Supported operator families:

- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Comparison: `==`, `!=`, `<`, `<=`, `>`, `>=`
- Logical: `&&`, `||`, `!`

Notes:

- `Integer / Integer` performs integer division.
- String concatenation requires strings; convert other values with `str(...)`.

```bump
print("xp: " + str(42));
```

## 6. Control Flow

### If / Else

```bump
if (score > 90) {
    print("S");
} else {
    print("A");
}
```

### While

```bump
while (hp > 0) {
    hp -= 1;
}
```

### For (classic)

```bump
for (Integer i = 0; i < 3; i++) {
    print(i);
}
```

### For-each (enhanced)

```bump
for (Integer value : ArrayList<Integer>([1, 2, 3])) {
    print(value);
}
```

### Break / Continue

Both are supported in loops.

## 7. Functions

Named function declaration:

```bump
fun Integer add(Integer a, Integer b) {
    return a + b;
}
```

Expression-bodied form:

```bump
fun Integer add(Integer a, Integer b) => a + b;
```

Functions are values:

```bump
Function<Integer, Integer> inc = (Integer x) => x + 1;
print(inc(4));
```

`Function<...>` type parameters are ordered as:

- return type first
- then argument types

Example: `Function<String, Integer, Bool>` is `(Integer, Bool) -> String`.

## 8. Classes and Objects

Class declarations support fields and methods.

```bump
class Hero {
    String name;
    Integer xp = 0;

    fun void init(String name) {
        this.name = name;
    }

    fun void gain(Integer amount) {
        this.xp += amount;
    }
}
```

`init(...)` is the constructor hook and can be overloaded.

Supported OOP features:

- `this`
- inheritance with `extends`
- interface-style contracts with `implements`
- `super` calls/access
- `abstract class`
- member visibility: `public` and `private`

## 9. Enums and Pattern Matching

Enums can be plain, value-backed, or payload variants:

```bump
enum Status { TODO, DOING, DONE }
enum HttpStatus { OK = 200, NOT_FOUND = 404, UNKNOWN }
enum Option<T> { Some(T value), None }
```

Enum members expose common metadata:

- `.name`
- `.ordinal`
- `.value`

Switch works for normal values and enum patterns:

```bump
switch (maybeName) {
    case Some(x):
        print(x);
        break;
    case None:
        print("none");
        break;
}
```

## 10. Generics

Generics are supported on:

- classes: `class Box<T> { ... }`
- functions: `fun T identity<T>(T value) { ... }`
- enums: `enum Option<T> { ... }`

Type constraints use `implements`:

```bump
fun T min<T implements Comparable>(T a, T b) {
    if (a < b) {
        return a;
    }
    return b;
}
```

## 11. Exceptions and Error Handling

`try/catch/finally` is supported.

```bump
try {
    throw(ValueError());
} catch (Exception e) {
    print(e.message);
} finally {
    print("done");
}
```

Catch parameter is optional:

```bump
try {
    Integer x = 1 / 0;
} catch {
    print("caught");
}
```

## 12. Extend Blocks

You can add methods to an existing type with `extend`:

```bump
extend Iterable {
    fun ArrayList<T> collect() {
        return ArrayList<T>(this.collect_array());
    }
}
```

## 13. Java Interop Block

BumpLang supports an embedded Java escape hatch:

```bump
unsafe_java {
    // Java code executed by the runtime bridge
}
```

This is used in parts of the stdlib (for example, `DateTime`) and is intended for advanced/runtime-level integrations. Do not use this unless **absolutely** necessary.<br>
The LSP server provides a warning if you use this block, which can be disabled with the flag `--no-unsafe-java-warnings`

## 14. Standard Library Modules

Common modules in this repository:

- `stdlib.arrayList`
- `stdlib.iterable`
- `stdlib.option`
- `stdlib.result`
- `stdlib.strings`
- `stdlib.math`
- `stdlib.dateTime`
- `stdlib.io`
- `stdlib.collections`
- `stdlib.testing`

## 15. Practical Notes

- Use explicit conversions (`str`, `int`, `bool`, `Float(...)`) instead of assuming coercion (it probably won't).
- If switch logic can fall through, use `break` explicitly.
- Use `Option`/`Result` from stdlib for API-like code instead of null values.
