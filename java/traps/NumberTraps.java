import java.util.HashMap;
import java.util.Map;

/**
 * Executable proof of Java's silent number traps. Every claim is an assert, so run it
 * with assertions enabled:
 *
 *   javac NumberTraps.java
 *   java -ea NumberTraps
 *
 * Without -ea the JVM skips every assert, so main() refuses to run.
 */
public class NumberTraps {

    /** if/else chain: each branch only sees what the earlier checks let through. */
    static String bucket(int latencyMs) {
        if (latencyMs < 100) return "fast";
        else if (latencyMs < 500) return "ok";
        return latencyMs >= 2000 ? "timeout" : "slow";
    }

    static boolean throwsArithmetic(Runnable r) {
        try {
            r.run();
            return false;
        } catch (ArithmeticException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        boolean assertsOn = false;
        assert assertsOn = true; // only runs (and sets the flag) under -ea
        if (!assertsOn) {
            throw new IllegalStateException("run with assertions on: java -ea NumberTraps");
        }

        // 1. Int math overflows before the result is widened to long.
        long bad = 24 * 60 * 60 * 1000 * 1000;   // all ints: wraps, then widens
        long good = 24L * 60 * 60 * 1000 * 1000; // first operand long: whole chain is long
        assert bad == 500_654_080L : bad;
        assert good == 86_400_000_000L : good;

        // Overflow wraps around silently; the *Exact methods throw instead.
        int max = Integer.MAX_VALUE;
        assert max + 1 == Integer.MIN_VALUE;
        assert throwsArithmetic(() -> Math.addExact(max, 1));
        assert throwsArithmetic(() -> Math.multiplyExact(24 * 60 * 60 * 1000, 1000));

        // 2. A narrowing cast keeps only the low 32 bits.
        long big = 3_000_000_000L; // the L is required: 3 billion doesn't fit in an int
        int narrowed = (int) big;
        assert narrowed == -1_294_967_296 : narrowed;
        assert narrowed == big - (1L << 32); // same bits, read as signed
        assert throwsArithmetic(() -> Math.toIntExact(big));

        // 3. Unboxing a null Integer throws NullPointerException.
        Map<String, Integer> counts = new HashMap<>();
        boolean npe = false;
        try {
            int n = counts.get("missing"); // get() returns null; unboxing calls null.intValue()
            System.out.println(n);
        } catch (NullPointerException e) {
            npe = true;
        }
        assert npe;
        assert counts.getOrDefault("missing", 0) == 0;

        // 4. Boundaries decide which side of < and >= you land on.
        assert bucket(99).equals("fast");
        assert bucket(100).equals("ok");
        assert bucket(499).equals("ok");
        assert bucket(500).equals("slow");
        assert bucket(1999).equals("slow");
        assert bucket(2000).equals("timeout");

        System.out.println("all number traps confirmed");
    }
}
