# The protocol stack is a stack: encapsulation in Java

_Learned 2026-11 · BU CX651 Week 10 (Internetworking and Java)_

Sending data over TCP/IP means every layer wraps the data in its own header on the way
down, and the receiver strips them in reverse: last on, first off. That's a stack, and
Java's `Deque` models it directly. Runnable, with every claim checked by an `assert`:
[`net/Encapsulation.java`](net/Encapsulation.java).

```bash
cd java/net
javac Encapsulation.java
java -ea Encapsulation
```

## Four layers, one job each

| Layer | Answers | Addressed by | Examples |
|-------|---------|--------------|----------|
| Application | What does the data mean? | — | HTTP, DNS, SSH |
| Transport | Which **program**? | port | TCP, UDP |
| Internet | Which **machine**? | IP address, routed hop by hop | IP, ICMP |
| Link | Which device on **this one hop**? | MAC address | Ethernet, Wi-Fi |

## Wrapping on the way down

A record holds each layer. The loop wraps the current data once per layer, and Ethernet
also adds a 4-byte checksum trailer (FCS):

```java
record Layer(String name, int headerBytes, int trailerBytes) { }

static final List<Layer> SEND_ORDER = List.of(
        new Layer("TCP", 20, 0), new Layer("IP", 20, 0), new Layer("ETH", 14, 4));

static String encapsulate(String payload) {
    String current = payload;
    for (Layer layer : SEND_ORDER) {
        current = layer.trailerBytes() > 0
                ? "[" + layer.name() + "|" + current + "|FCS]"
                : "[" + layer.name() + "|" + current + "]";
    }
    return current;
}
// "GET /" -> "[ETH|[IP|[TCP|GET /]]|FCS]"
```

## Stripping on the way up

Push the layers in sending order and pop them back out, and you get the receiving
order for free:

```java
Deque<Layer> stack = new ArrayDeque<>();
for (Layer layer : SEND_ORDER) stack.push(layer);   // TCP, IP, ETH (ETH on top)
while (!stack.isEmpty()) {
    Layer layer = stack.pop();                       // ETH, then IP, then TCP
    // check the wrapper matches, then cut it off
}
```

`decapsulate()` also checks that each wrapper is the one it expects, so a malformed
frame fails loudly instead of returning half-stripped data.

## Why small messages are expensive

The wrappers cost 20 + 20 + 14 + 4 = **58 bytes** no matter how small the payload is:

| Payload (bytes) | Frame (bytes) | Headers |
|----------------:|--------------:|--------:|
| 1 | 59 | 98.31% |
| 42 | 100 | 58.00% |
| 536 | 594 | 9.76% |
| 1460 | 1518 | 3.82% |

That's why sending one row per request is slow, and batching rows into fewer, fuller
packets is fast. 1460 is the most data one packet can hold: Ethernet's MTU is 1500
bytes per IP packet, minus 20 for IP and 20 for TCP. A message needs
`ceil(bytes / 1460)` packets, computed with ints only:

```java
int room = 1500 - 20 - 20;                 // 1460
long packets = (bytes + room - 1) / room;  // 1 MB (10^6) -> 685, 1 MiB (2^20) -> 719
```

## Two Java details from the overhead math

```java
double pct = (double) overhead / total * 100;      // cast BEFORE dividing, or 58 / 100 = 0
double rounded = Math.round(pct * 100) / 100.0;    // 100.0, not 100, keeps the decimals
```

**Why `Math.round(double)` returns a `long`:** a `double` can hold whole numbers far
past `Integer.MAX_VALUE` (about 2.1 billion), and an `int` result would silently be
wrong for them. `Math.round(5e9)` is 5,000,000,000L, and `(int)` of that is
705,032,704. It bites when you assign the result back to an `int`: that's a compile
error, and the tempting fix, an `(int)` cast, hides the overflow. The `float` overload,
`Math.round(2.5f)`, is the one that returns `int`.

## Where does ARP belong?

The lesson's table puts ARP in the Internet layer, but it works with MAC addresses, and
both views have a point. ARP answers an Internet-layer question ("which MAC has IP
10.0.0.7?") with a Link-layer answer, and its messages travel directly inside Ethernet
frames with **no IP header**, so they never leave the local network. It's usually
described as glue between the two layers. `layerOf("ARP")` in the code says so instead
of picking one.
