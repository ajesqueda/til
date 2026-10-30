# Java's silent number traps: overflow, narrowing, null unboxing

_Learned 2026-10 · BU CX651 Week 9 (Java for experienced programmers)_

Python ints never overflow and `None` never turns into a number. Java has fixed-size
primitives (`int` is 32 bits, `long` is 64) and wrapper objects (`Integer`) that can be
`null`, and none of the traps below raise an error by default. They're all proven with
`assert` in [`traps/NumberTraps.java`](traps/NumberTraps.java), with the Python side in
[`traps/number_traps.py`](traps/number_traps.py).

```bash
cd java/traps
javac NumberTraps.java
java -ea NumberTraps        # -ea turns asserts on; without it main() refuses to run
python number_traps.py
```

## 1. Int math overflows before it's stored in a `long`

```java
long bad  = 24 * 60 * 60 * 1000 * 1000;   // 500654080
long good = 24L * 60 * 60 * 1000 * 1000;  // 86400000000
```

The type of an expression comes from its operands, not from the variable it's assigned
to. Every literal on the right of `bad` is an `int`, so the multiplication happens in
32 bits, wraps around, and only *then* gets widened to `long`. One `L` on the first
operand makes the whole chain `long` math.

`javap -c` shows it's worse than a runtime bug. `javac` evaluates constant expressions
at compile time, so the wrong number is baked into the class file:

```
36: ldc2_w        #38    // long 500654080l
39: lstore_2              // bad
40: ldc2_w        #40    // long 86400000000l
43: lstore        4       // good
```

There's no multiply instruction left to inspect, only the already-wrong answer.
Python's compiler folds it too (`dis` shows `LOAD_CONST 86400000000`), but Python gets
the right answer.

When overflow should be an error rather than a wrap, use `Math.addExact`,
`Math.multiplyExact` and friends. They throw `ArithmeticException`.

## 2. A narrowing cast keeps the low 32 bits

```java
long big = 3_000_000_000L;   // needs the L: 3 billion doesn't fit in an int literal
int narrowed = (int) big;    // -1294967296
```

The cast compiles to one instruction, `l2i`, which drops the top 32 bits with no
range check:

```
172: ldc2_w        #59   // long 3000000000l
177: lload         7
179: l2i                  // long -> int: keep low 32 bits
180: istore        9
```

3,000,000,000 is above 2³¹ − 1, so its 32-bit pattern has the sign bit set and reads as
3,000,000,000 − 2³² = −1,294,967,296. `Math.toIntExact(big)` throws instead.
In Python, `to_int32()` in `number_traps.py` does the same masking explicitly:
`n & 0xFFFFFFFF`, then subtract 2³² if the sign bit is set.

## 3. Unboxing `null` is a `NullPointerException`

```java
Map<String, Integer> counts = new HashMap<>();
int n = counts.get("missing");   // NullPointerException
```

The line has no visible method call on `null`, but the bytecode has one. Assigning an
`Integer` to an `int` makes the compiler insert `intValue()`:

```
275: invokeinterface #75, 2  // Map.get(Object) -> Object   (returns null)
280: checkcast     #45       // Integer                     (null passes a cast)
283: invokevirtual #81       // Integer.intValue()          <- the NPE happens here
286: istore        12
```

Fix it with `counts.getOrDefault("missing", 0)`. Python's version of this bug is
`counts.get("missing") + 1`, which raises `TypeError`, and it's fixed the same way:
`counts.get("missing", 0)`.

## 4. if/else chains are about what's left over

```java
static String bucket(int latencyMs) {
    if (latencyMs < 100) return "fast";
    else if (latencyMs < 500) return "ok";
    return latencyMs >= 2000 ? "timeout" : "slow";   // cond ? ifTrue : ifFalse
}
```

Each branch only sees values the earlier checks didn't return on, so the final line
handles "500 and up" without saying so. Test the edges on both sides of every
comparison: 99/100, 499/500 and 1999/2000.

## Bonus: what `assert` compiles to

Each `assert` becomes a check of a hidden static field. When assertions are off (the
default), the check jumps straight past the test:

```
45: getstatic     #22    // Field $assertionsDisabled:Z
48: ifne          68     // disabled -> skip the whole assert
```

That's why `java NumberTraps` without `-ea` would "pass" every assert. `main()` guards
against it with `assert assertsOn = true;`, an assignment that only runs when
assertions are on.

## When to reach for `javap -c`

You don't need it to learn the rules. Reach for it when the output surprises you or you
suspect the compiler inserted something: a hidden `intValue()`, a folded constant, an
`l2i`. Here the bytecode settled two questions faster than reading about them: where
the NPE comes from, and why the overflow can't be seen at runtime.
