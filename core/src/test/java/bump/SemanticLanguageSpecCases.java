package bump;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class SemanticLanguageSpecCases {
    private static final List<SemanticCase> CASES = List.of(
            SemanticCase.success(
                    "program-structure-and-blocks",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    Integer x = 1;
                    {
                        Integer y = 2;
                        print(x + y);
                    }
                    print(x);
                    """,
                    """
                    3
                    1
                    """,
                    Set.of(1)
            ),
            SemanticCase.success(
                    "import-directive",
                    SemanticTestHarness.RunMode.WITH_IMPORT_DIRECTIVES,
                    """
                    import stdlib.arrayList;
                    import stdlib.option;
                    ArrayList<Integer> list = ArrayList<Integer>([1, 2, 3]);
                    print(list.size());
                    print(Option<Integer>.Some(9).unwrap());
                    """,
                    """
                    3
                    9
                    """,
                    Set.of(2)
            ),
            SemanticCase.success(
                    "built-in-types-and-literals",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    print(true);
                    print(null == null);
                    Array items = [1, 2];
                    Map values = {"name": "Ada", "count": 1};
                    print(items.length());
                    print(values.get("name"));
                    """,
                    """
                    true
                    true
                    2
                    Ada
                    """,
                    Set.of(3)
            ),
            SemanticCase.success(
                    "variables-assignment-and-step-operators",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    Integer value = 10;
                    value += 2;
                    print(value);
                    print(value++);
                    print(value);
                    print(--value);
                    """,
                    """
                    12
                    12
                    13
                    12
                    """,
                    Set.of(4)
            ),
            SemanticCase.success(
                    "expressions-and-operators",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    print(1 + 2 * 3);
                    print(7 % 4);
                    print((3 > 2) && !(1 == 2));
                    """,
                    """
                    7
                    3
                    true
                    """,
                    Set.of(5)
            ),
            SemanticCase.success(
                    "control-flow-if-for-while-break-continue",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    Integer total = 0;
                    for (Integer i = 0; i < 6; i++) {
                        if (i == 1) {
                            continue;
                        }
                        if (i == 4) {
                            break;
                        }
                        total += i;
                    }
                    Integer guard = 2;
                    while (guard > 0) {
                        guard -= 1;
                    }
                    if (guard == 0) {
                        print(total);
                    } else {
                        print(999);
                    }
                    """,
                    """
                    5
                    """,
                    Set.of(6)
            ),
            SemanticCase.success(
                    "functions-and-lambdas",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    fun Integer add(Integer left, Integer right) => left + right;
                    Function<Integer, Integer> inc = (Integer x) => x + 1;
                    print(add(2, 3));
                    print(inc(4));
                    """,
                    """
                    5
                    5
                    """,
                    Set.of(7)
            ),
            SemanticCase.success(
                    "classes-inheritance-and-super",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    class Animal {
                        fun String speak() {
                            return "???";
                        }
                    }

                    class Dog extends Animal {
                        fun String speak() {
                            return super.speak() + " woof";
                        }
                    }

                    print(Dog().speak());
                    """,
                    """
                    ??? woof
                    """,
                    Set.of(8)
            ),
            SemanticCase.success(
                    "enums-and-switch-patterns",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    enum Option<T> { Some(T value), None }
                    Option<Integer> found = Option<Integer>.Some(7);
                    switch (found) {
                        case Some(x):
                            print(x);
                            break;
                        case None:
                            print(-1);
                            break;
                    }
                    """,
                    """
                    7
                    """,
                    Set.of(9)
            ),
            SemanticCase.success(
                    "generics-on-class-function-and-enum",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    class Box<T> {
                        T value;
                        fun void init(T value) {
                            this.value = value;
                        }
                    }

                    fun T identity<T>(T value) {
                        return value;
                    }

                    enum Wrapper<U> { Item(U value) }

                    Box<Integer> box = Box<Integer>(identity<Integer>(8));
                    Wrapper<String> wrapped = Wrapper<String>.Item("ok");
                    print(box.value);
                    print(wrapped.value);
                    """,
                    """
                    8
                    ok
                    """,
                    Set.of(10)
            ),
            SemanticCase.success(
                    "exceptions-try-catch-finally",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    try {
                        throw(ValueError());
                    } catch (Exception e) {
                        print("caught");
                    } finally {
                        print("finally");
                    }
                    """,
                    """
                    caught
                    finally
                    """,
                    Set.of(11)
            ),
            SemanticCase.success(
                    "extend-blocks",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    class Score {
                        Integer value = 7;
                    }

                    extend Score {
                        fun Integer doubled() {
                            return this.value * 2;
                        }
                    }

                    print(Score().doubled());
                    """,
                    """
                    14
                    """,
                    Set.of(12)
            ),
            SemanticCase.success(
                    "java-interop-via-datetime-stdlib",
                    SemanticTestHarness.RunMode.WITH_STDLIB_IMPORTS,
                    """
                    DateTime instant = DateTime("2024-04-03T12:00:00");
                    print(instant.year());
                    print(instant.to_string());
                    """,
                    """
                    2024
                    2024-04-03T12:00:00
                    """,
                    Set.of(13)
            ),
            SemanticCase.success(
                    "stdlib-modules-smoke-test",
                    SemanticTestHarness.RunMode.WITH_IMPORT_DIRECTIVES,
                    """
                    import stdlib.arrayList;
                    import stdlib.option;
                    import stdlib.result;
                    import stdlib.iterable;
                    import stdlib.strings;
                    import stdlib.math;
                    import stdlib.dateTime;
                    import stdlib.collections;
                    import stdlib.io;
                    import stdlib.testing;

                    ArrayList<Integer> list = ArrayList<Integer>([3, 1, 2]);
                    print(list.sort().collect().join(","));
                    print(Option<Integer>.Some(5).unwrap());

                    Result<Integer, ValueError> ok = Result<Integer, ValueError>();
                    ok.init_ok(9);
                    print(ok.unwrap());

                    print("  hi there  ".words().length());
                    print(max(2, 7));
                    print(DateTime("2024-01-01T00:00:00").year());

                    HashSet<String> tags = HashSet<String>();
                    tags.add("x");
                    print(tags.contains("x"));

                    File f = File("../README.md");
                    print(f.exists());
                    print(f.size_bytes() > 0);

                    assert_true(true);
                    print("assert");
                    """,
                    """
                    1,2,3
                    5
                    9
                    2
                    7
                    2024
                    true
                    true
                    true
                    assert
                    """,
                    Set.of(14)
            ),
            SemanticCase.success(
                    "practical-notes-explicit-conversions-and-typeof",
                    SemanticTestHarness.RunMode.RAW,
                    """
                    print("xp=" + str(int("7")));
                    print(type_of(1) == Integer);
                    print(bool(0));
                    print(bool(2));
                    """,
                    """
                    xp=7
                    true
                    false
                    true
                    """,
                    Set.of(15)
            ),
            SemanticCase.failure(
                    "import-errors-are-reported",
                    SemanticTestHarness.RunMode.WITH_IMPORT_DIRECTIVES,
                    """
                    import arraylist;
                    """,
                    "must use dotted module format",
                    Set.of(2)
            )
    );

    private SemanticLanguageSpecCases() {}

    static Stream<SemanticCase> successCases() {
        return CASES.stream().filter(testCase -> testCase.expectedErrorPart() == null);
    }

    static Stream<SemanticCase> failureCases() {
        return CASES.stream().filter(testCase -> testCase.expectedErrorPart() != null);
    }

    static Set<Integer> coveredSections() {
        return CASES.stream()
                .flatMap(testCase -> testCase.specSections().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    static IntStream requiredSections() {
        return IntStream.rangeClosed(1, 15);
    }
}
