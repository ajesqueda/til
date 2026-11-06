# IPv4 addresses are 32-bit numbers: bit math in Java

_Learned 2026-11 · BU CX651 Week 10 (Internetworking and Java)_

An IP address picks the **machine** and a port picks the **program** on it. The dotted
form `192.168.1.100` is just a way to write one 32-bit number, 8 bits per octet.
[`net/IpMath.java`](net/IpMath.java) converts both ways, checks private ranges and
CIDR blocks, and asserts every edge case. The tests cross-check it against Python's
`ipaddress` module on 300 random addresses.

```bash
cd java/net
javac IpMath.java
java -ea IpMath                          # self-checks
java IpMath toLong 192.168.1.100         # 3232235876
java IpMath toIp 3232235876              # 192.168.1.100
```

## Dotted string → number

```java
static long ipv4ToLong(String dotted) {
    long value = 0;
    for (String part : dotted.split("\\.", -1)) {    // "." is regex for "any char"
        value = (value << 8) | Integer.parseInt(part); // plus validation in the real file
    }
    return value;
}
// and back: (v >> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF
```

It has to be a `long`. Java's `int` is signed, so it stops at 2,147,483,647 (that's
`127.255.255.255`), and every address from `128.0.0.0` up would come out negative.
Python's equivalent is just `int(ipaddress.ip_address("192.168.1.100"))`.

The real version rejects `256.1.1.1`, `1.2.3`, `1..2.3`, `1.2.3.-4` and `1.2.3.4.`.
That last one is why the `-1` in `split("\\.", -1)` matters: without it, Java
silently drops trailing empty strings, so `"1.2.3.4."` splits into 4 parts and would
be accepted as `1.2.3.4`.

## Why `|` works like `+` here

`value << 8` multiplies by 256 *and* leaves the low 8 bits as zeros. OR-ing the octet in
fills those empty bits, and when no bit is set on both sides, nothing carries, so `|`
and `+` agree. They differ as soon as bits overlap: `3 | 1 == 3`, but `3 + 1 == 4`.
`|` is the safer choice because it can never spill into the octet above.

## `& 0xFF` vs `% 256`

Keeping the low 8 bits (`& 0xFF`) is the same as the remainder mod 256, **for
non-negative numbers**. For negative ones Java's `%` keeps the sign: `-1 % 256 == -1`,
but `-1 & 0xFF == 255`. Masks are what you want for bytes.

## Signed bytes: `(byte) 192 == -64`

Java's `byte` is signed (−128..127). 192 is `1100_0000`, and with the top bit read as
negative it means 192 − 256 = −64. That's why raw packet bytes look "wrong" when
printed. `b & 0xFF` widens the byte to an `int` and keeps only the 8 bits, so you get
192 back.

## Private ranges and CIDR

A block like `172.16.0.0/12` means "the first 12 bits are fixed". Two addresses are in
the same block when their first 12 bits match, so compare them after masking:

```java
static long prefixMask(int prefix) {          // /12 -> 0xFFF00000
    return prefix == 0 ? 0 : (0xFFFF_FFFFL << (32 - prefix)) & 0xFFFF_FFFFL;
}
static boolean inCidr(String cidr, String ip) {
    String[] p = cidr.split("/");
    long mask = prefixMask(Integer.parseInt(p[1]));
    return (ipv4ToLong(ip) & mask) == (ipv4ToLong(p[0]) & mask);
}
```

The three private (RFC 1918) ranges are `10.0.0.0/8`, `172.16.0.0/12` and
`192.168.0.0/16`. The `/12` is the easy one to get wrong: it covers `172.16.x` through
`172.31.x`, so `172.31.255.255` is private and `172.32.0.0` isn't. `/0` ("match
everything") is checked too. With a `long` the shift by 32 works, but the same code on
an `int` would break: Java only uses the low 5 bits of an `int` shift count, so
`x << 32` is `x << 0`, and a `/0` mask would come out as all ones instead of all zeros.

## Wrapping sequence numbers, and why a SYN uses one

TCP numbers every byte it sends with a 32-bit sequence number, and it wraps to 0
after 4,294,967,295. Java has no unsigned `int`, so use a `long` and mask:

```java
static long nextSeq(long seq, long bytes) { return (seq + bytes) & 0xFFFF_FFFFL; }
// nextSeq(4294967295L, 1) == 0
```

A SYN carries no data but still uses up one sequence number, and so does a FIN. TCP
only acknowledges things that have sequence numbers, so giving the SYN one is what lets
the other side ACK it without ambiguity: `ack = ISN + 1` means "I got your SYN, and the
next byte I expect is ISN + 1". Without it, an ACK for the SYN and an ACK for the first
data byte would look the same.

## Catch, or declare `throws`?

A checked exception like `UnknownHostException` has to be caught or declared:

- **Catch it** where you can actually do something about it: retry, fall back, or turn
  it into a result like `"DNS FAILED: host"`.
- **Declare `throws`** when this method can't fix it and the caller is better placed to
  decide (a library helper usually shouldn't swallow a DNS failure).

`IpMath` throws `IllegalArgumentException`, an *unchecked* exception, for a malformed
address. Bad input is a bug in the caller's data, not an expected condition every
caller must handle, which is the usual dividing line.
