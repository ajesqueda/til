"""The Python side of NumberTraps.java: which traps Python has, and which it doesn't."""

import dis
import io


def to_int32(n: int) -> int:
    """What Java's (int) cast does: keep the low 32 bits, read them as signed."""
    n &= 0xFFFF_FFFF
    return n - (1 << 32) if n >= 1 << 31 else n


def day_in_microseconds() -> int:
    # Python ints are arbitrary precision, so there's no overflow to dodge.
    return 24 * 60 * 60 * 1000 * 1000


def bucket(latency_ms: int) -> str:
    if latency_ms < 100:
        return "fast"
    elif latency_ms < 500:
        return "ok"
    return "timeout" if latency_ms >= 2000 else "slow"


def folded_constant() -> int:
    return 24 * 60 * 60 * 1000 * 1000


def bytecode(func) -> str:
    """dis output as a string (Python's javap -c)."""
    out = io.StringIO()
    dis.dis(func, file=out)
    return out.getvalue()


if __name__ == "__main__":
    print(day_in_microseconds())           # 86400000000, no L needed
    print(to_int32(day_in_microseconds()))  # 500654080, what Java's int math gives
    print(to_int32(3_000_000_000))          # -1294967296
    print(bytecode(folded_constant))        # LOAD_CONST 86400000000: folded at compile time
