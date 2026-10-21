# Reading JVM bytecode with `javap -c`

_Learned 2026-10 · BU CX651 Week 8 (Java and the JVM)_

`javac` doesn't produce machine code. It produces **bytecode** for the Java Virtual
Machine, a made-up CPU that has a stack instead of registers. `javap -c` disassembles
a `.class` file so you can read that bytecode.

## A loop, compiled

```java
static int sumTo(int n) {
    int total = 0;
    for (int i = 1; i <= n; i++) {
        total += i;
    }
    return total;
}
```

```bash
javac SumTo.java
javap -c SumTo
```

```
static int sumTo(int);
  Code:
     0: iconst_0              // push 0
     1: istore_1              // pop -> local 1 (total = 0)
     2: iconst_1              // push 1
     3: istore_2              // pop -> local 2 (i = 1)
     4: iload_2               // push i            <-- loop check
     5: iload_0               // push n
     6: if_icmpgt     19      // pop both; if i > n jump to 19 (exit)
     9: iload_1               // push total
    10: iload_2               // push i
    11: iadd                  // pop two, push total + i
    12: istore_1              // pop -> total
    13: iinc          2, 1    // i++ (done in place, no stack)
    16: goto          4       // back to the check
    19: iload_1               // push total
    20: ireturn               // return top of stack
```

## What I noticed

- **A stack machine, not registers.** An x86 CPU says `add rdi, rax`. The JVM pushes
  two values, `iadd` pops both and pushes the result. Locals live in numbered slots:
  slot 0 is the parameter `n`, slot 1 is `total`, slot 2 is `i`.
- **The type is in the opcode.** The `i` in `iload`/`iadd`/`ireturn` means `int`. A
  `double` would use `dload`/`dadd`. This is the static typing showing up in bytecode.
- **Loops are still jumps.** It's the same shape as x86 machine code: compare
  (`if_icmpgt`, which inverts `<=` to "jump out if greater"), the body, then `goto`
  back to the check. There's no `for` instruction. Compare with my
  [toy CPU](https://github.com/ajesqueda/ai-concepts-lab/tree/main/toy-cpu).
- **Numbers on the left are byte offsets,** not line numbers. That's why they skip
  (`6` → `9`): `if_icmpgt` takes 3 bytes because its operand is the jump target.

At runtime the JVM interprets this bytecode at first. Once a method gets "hot", the
JIT compiler translates it into real machine code for the CPU it's running on.
