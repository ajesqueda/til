import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The TCP/IP stack as Java data structures: each layer wraps the data on the way down,
 * and the receiver strips the wrappers in reverse, which is exactly a stack.
 *
 *   javac Encapsulation.java
 *   java -ea Encapsulation
 */
public class Encapsulation {

    /** A layer and the bytes it adds. Records get name(), headerBytes(), equals, toString. */
    record Layer(String name, int headerBytes, int trailerBytes) { }

    /** Sending order: the transport header goes on first, the link frame last. */
    static final List<Layer> SEND_ORDER = List.of(
            new Layer("TCP", 20, 0),
            new Layer("IP", 20, 0),
            new Layer("ETH", 14, 4)); // Ethernet adds a 4-byte FCS checksum at the end

    static final int MTU = 1500; // max IP packet on Ethernet

    /** Wrap the payload once per layer, going down the stack. */
    static String encapsulate(String payload) {
        String current = payload;
        for (Layer layer : SEND_ORDER) {
            current = layer.trailerBytes() > 0
                    ? "[" + layer.name() + "|" + current + "|FCS]"
                    : "[" + layer.name() + "|" + current + "]";
        }
        return current;
    }

    /** Strip one wrapper per layer, last on first off. Returns the payload. */
    static String decapsulate(String frame, List<String> strippedOut) {
        Deque<Layer> stack = new ArrayDeque<>();
        for (Layer layer : SEND_ORDER) {
            stack.push(layer); // push = add to the top
        }
        String current = frame;
        while (!stack.isEmpty()) {
            Layer layer = stack.pop(); // pop = take from the top: ETH first
            String open = "[" + layer.name() + "|";
            String close = layer.trailerBytes() > 0 ? "|FCS]" : "]";
            if (!current.startsWith(open) || !current.endsWith(close)) {
                throw new IllegalArgumentException("expected a " + layer.name() + " wrapper: " + current);
            }
            current = current.substring(open.length(), current.length() - close.length());
            strippedOut.add(layer.name());
        }
        return current;
    }

    static int overheadBytes() {
        int total = 0;
        for (Layer layer : SEND_ORDER) {
            total += layer.headerBytes() + layer.trailerBytes();
        }
        return total;
    }

    static int frameBytes(int payloadBytes) {
        return payloadBytes + overheadBytes();
    }

    /** Percent of the frame that is headers, rounded to 2 decimals. */
    static double overheadPercent(int payloadBytes) {
        double pct = (double) overheadBytes() / frameBytes(payloadBytes) * 100; // cast BEFORE dividing
        return Math.round(pct * 100) / 100.0; // round() gives a long; 100.0 keeps the decimals
    }

    /** Packets needed for a message: ceiling division using ints only. */
    static long packetsFor(long bytes) {
        int room = MTU - 20 - 20; // data left after IP and TCP headers: 1460
        return (bytes + room - 1) / room;
    }

    /** Which layer a protocol lives in. Arrow-form switch: no fall-through. */
    static String layerOf(String protocol) {
        return switch (protocol.toUpperCase()) {
            case "HTTP", "DNS", "SMTP", "SSH" -> "Application";
            case "TCP", "UDP" -> "Transport";
            case "IP", "ICMP" -> "Internet";
            case "ARP" -> "Link (resolves an IP to a MAC; its frames carry no IP header)";
            case "ETHERNET", "WIFI" -> "Link";
            default -> "Unknown";
        };
    }

    public static void main(String[] args) {
        boolean assertsOn = false;
        assert assertsOn = true;
        if (!assertsOn) {
            throw new IllegalStateException("run with assertions on: java -ea Encapsulation");
        }

        String frame = encapsulate("GET /");
        assert frame.equals("[ETH|[IP|[TCP|GET /]]|FCS]") : frame; // .equals, never ==

        List<String> stripped = new ArrayList<>();
        assert decapsulate(frame, stripped).equals("GET /");
        assert stripped.equals(List.of("ETH", "IP", "TCP")) : stripped; // reverse of SEND_ORDER

        assert overheadBytes() == 58;
        assert frameBytes(42) == 100;
        assert overheadPercent(42) == 58.0;
        assert overheadPercent(1460) == 3.82;
        assert frameBytes(1460) == 1518; // the classic max Ethernet frame (without preamble)

        assert packetsFor(1460) == 1;
        assert packetsFor(1461) == 2;
        assert packetsFor(1_000_000) == 685;      // 1 MB
        assert packetsFor(1L << 20) == 719;       // 1 MiB = 1,048,576 bytes

        // Math.round(double) returns long: a double can hold whole numbers far past
        // Integer.MAX_VALUE, so an int result could silently be wrong.
        long big = Math.round(5e9);
        assert big == 5_000_000_000L;
        assert (int) big != big; // squeezing it back into an int loses it
        assert Math.round(2.5f) == 3; // the float overload is the one that returns int

        assert layerOf("dns").equals("Application");
        assert layerOf("udp").equals("Transport");
        assert layerOf("ARP").startsWith("Link");
        assert layerOf("QUIC").equals("Unknown");

        System.out.println("payload  frame  headers");
        for (int payload : new int[] {1, 42, 536, 1460}) {
            System.out.printf("%7d  %5d  %6.2f%%%n", payload, frameBytes(payload), overheadPercent(payload));
        }
        System.out.println("all encapsulation checks passed");
    }
}
