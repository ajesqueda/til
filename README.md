# TIL — Today I Learned

Short notes on things I learned, each with a small code example. Entries are
grouped by topic.

## Index

### Java

- [Java for Python developers: the side-by-side](java/java-for-python-devs.md) — types, collections, classes, interfaces and exceptions next to their Python equivalents
- [Reading JVM bytecode with `javap -c`](java/javap-bytecode.md) — a `for` loop compiled to stack-machine bytecode
- [Java's silent number traps](java/number-traps.md) — int overflow, narrowing casts and null unboxing, proven with `java -ea` and read in `javap -c`
- [The protocol stack is a stack](java/encapsulation-as-a-stack.md) — TCP/IP encapsulation with records and a `Deque`, header overhead by payload size, and where ARP really sits
- [IPv4 addresses are 32-bit numbers](java/ipv4-bit-math.md) — dotted ↔ long, CIDR masks and private ranges, signed bytes, `|` vs `+`, and wrapping TCP sequence numbers
