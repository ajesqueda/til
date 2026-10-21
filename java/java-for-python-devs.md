# Java for Python developers: the side-by-side

_Learned 2026-10 · BU CX651 Week 8 (Java and the JVM)_

The biggest shift from Python is that **Java checks types at compile time**. Every
variable has a declared type, and the compiler refuses code where types don't line up.
In Python that same mistake would only show up when the line runs. Everything else
mostly follows from that, plus "all code lives inside a class."

Every Java snippet below was compiled and run with OpenJDK 25.

## Variables and types

```python
age = 30
price = 9.99
active = True
name = "Alfonso"
MAX_RETRIES = 3          # constant by convention only
```

```java
int age = 30;            // primitive: the value itself lives in the variable
double price = 9.99;
boolean active = true;   // lowercase true/false
String name = "Alfonso"; // reference type: the variable holds a pointer to an object
var total = 0L;          // `var` infers the type (here long), but it's still fixed
final int MAX_RETRIES = 3;  // `final` = can't be reassigned, enforced by the compiler
```

**Primitive vs reference.** `int`, `double`, `boolean`, `long` and `char` store the
value directly. Everything else (`String`, lists, your classes) is a reference to an
object on the heap. That's why `==` on strings compares *references*. Use `.equals()`
to compare their contents.

## Lists and dicts

```python
tables = ["orders", "customers"]
row_counts = {"orders": 1200}
row_counts.get("missing", 0)
```

```java
List<String> tables = new ArrayList<>();       // <String> = element type, checked
tables.add("orders");
tables.get(0);                                 // not tables[0]
tables.size();                                 // not len(tables)

Map<String, Integer> rowCounts = new HashMap<>();
rowCounts.put("orders", 1200);
rowCounts.getOrDefault("missing", 0);
```

Collections hold objects, not primitives, so it's `List<Integer>`, never `List<int>`.
Java converts between the two automatically ("autoboxing").

## Loops and conditionals

```python
for t in tables:
    print(t.upper())
for i in range(3):
    total += i
label = "adult" if age >= 18 else "minor"
```

```java
for (String t : tables) {
    System.out.println(t.toUpperCase());
}
for (int i = 0; i < 3; i++) {
    total += i;
}
String label = (age >= 18) ? "adult" : "minor";
```

Braces define blocks, so indentation is only for humans. Every statement ends in `;`.

## Classes

```python
class Counter:
    def __init__(self, name):
        self.name = name
        self._count = 0

    def increment(self):
        self._count += 1
```

```java
class Counter {
    private int count = 0;         // fields are declared up front, with types
    private final String name;     // `private` is enforced, unlike Python's `_`

    Counter(String name) {         // constructor = method named after the class
        this.name = name;          // `this` is Python's `self`, but implicit
    }

    void increment() {             // `void` = returns nothing
        count++;
    }
}

Counter c = new Counter("jobs");  // `new` is required
```

## Interfaces vs duck typing

Python is happy as long as an object *has* an `area()` method. Java wants the promise
written down:

```java
interface Shape {
    double area();
}

record Rect(double w, double h) implements Shape {   // record ≈ @dataclass(frozen=True)
    public double area() { return w * h; }
}

Shape s = new Rect(2, 3);
```

## Exceptions

```java
try {
    int bad = Integer.parseInt("abc");
} catch (NumberFormatException e) {   // catch by type, like `except ValueError as e`
    System.out.println("not a number: " + e.getMessage());
}
```

## Program entry point

```java
public class Cheatsheet {
    public static void main(String[] args) {   // Python's `if __name__ == "__main__":`
        System.out.printf("%s is %d%n", "Alfonso", 30);   // printf-style f-string
    }
}
```

`javac Cheatsheet.java` compiles to `Cheatsheet.class` (bytecode), and `java Cheatsheet`
runs it on the JVM. See [what that bytecode looks like](javap-bytecode.md).
