/**
 * IPv4 addresses as 32-bit numbers: parsing, formatting, private ranges, CIDR blocks,
 * and wrapping TCP sequence numbers. Java has no unsigned int, so everything that
 * holds a full 32-bit value is a long.
 *
 *   javac IpMath.java
 *   java -ea IpMath                       # run every self-check
 *   java -ea IpMath toLong 10.0.0.1 ...   # one number per line
 *   java -ea IpMath toIp 167772161 ...    # one dotted address per line
 */
public class IpMath {

    static final long MASK_32 = 0xFFFF_FFFFL;

    /** "192.168.1.100" -> 3232235876. Each octet is worth 256x the next. */
    static long ipv4ToLong(String dotted) {
        String[] parts = dotted.split("\\.", -1); // "." is a regex wildcard, so escape it
        if (parts.length != 4) {
            throw new IllegalArgumentException("need 4 octets: " + dotted);
        }
        long value = 0;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("bad octet '" + part + "' in " + dotted);
            }
            int octet = Integer.parseInt(part);
            if (octet > 255) {
                throw new IllegalArgumentException("octet over 255 in " + dotted);
            }
            value = (value << 8) | octet; // <<8 = x256 and leaves 8 empty bits for | to fill
        }
        return value;
    }

    /** 3232235876 -> "192.168.1.100". Shift each octet down, keep its 8 bits. */
    static String longToIpv4(long value) {
        if (value < 0 || value > MASK_32) {
            throw new IllegalArgumentException("not a 32-bit address: " + value);
        }
        return ((value >> 24) & 0xFF) + "." + ((value >> 16) & 0xFF) + "."
                + ((value >> 8) & 0xFF) + "." + (value & 0xFF);
    }

    /** Network mask for a prefix length: /12 -> 0xFFF00000. */
    static long prefixMask(int prefix) {
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("prefix must be 0-32: " + prefix);
        }
        return prefix == 0 ? 0 : (MASK_32 << (32 - prefix)) & MASK_32;
    }

    /** Is ip inside the block "a.b.c.d/n"? Same network bits = same block. */
    static boolean inCidr(String cidr, String ip) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected a.b.c.d/n: " + cidr);
        }
        long mask = prefixMask(Integer.parseInt(parts[1]));
        return (ipv4ToLong(ip) & mask) == (ipv4ToLong(parts[0]) & mask);
    }

    /** RFC 1918 private ranges: never routed on the public internet. */
    static boolean isPrivate(String ip) {
        return inCidr("10.0.0.0/8", ip) || inCidr("172.16.0.0/12", ip) || inCidr("192.168.0.0/16", ip);
    }

    /** TCP sequence numbers are 32-bit and wrap around to 0. */
    static long nextSeq(long seq, long bytes) {
        return (seq + bytes) & MASK_32;
    }

    static void selfCheck() {
        boolean assertsOn = false;
        assert assertsOn = true;
        if (!assertsOn) {
            throw new IllegalStateException("run with assertions on: java -ea IpMath");
        }

        assert ipv4ToLong("192.168.1.100") == 3_232_235_876L; // past Integer.MAX_VALUE
        assert ipv4ToLong("0.0.0.0") == 0;
        assert ipv4ToLong("255.255.255.255") == MASK_32;
        for (String ip : new String[] {"0.0.0.0", "1.2.3.4", "10.0.0.1", "255.255.255.255"}) {
            assert longToIpv4(ipv4ToLong(ip)).equals(ip) : ip; // round trip
        }

        for (String bad : new String[] {"256.1.1.1", "1.2.3", "1.2.3.4.5", "1..2.3", "a.b.c.d", "1.2.3.-4", "1.2.3.4."}) {
            boolean rejected = false;
            try {
                ipv4ToLong(bad);
            } catch (IllegalArgumentException e) {
                rejected = true;
            }
            assert rejected : bad;
        }

        assert isPrivate("10.255.0.1");
        assert isPrivate("172.16.0.0") && isPrivate("172.31.255.255"); // /12 ends at 172.31
        assert !isPrivate("172.32.0.0") && !isPrivate("172.15.255.255");
        assert isPrivate("192.168.86.1");
        assert !isPrivate("8.8.8.8");
        assert prefixMask(12) == 0xFFF0_0000L && prefixMask(32) == MASK_32 && prefixMask(0) == 0;
        assert inCidr("0.0.0.0/0", "203.0.113.9"); // /0 matches everything
        assert (1 << 32) == 1 && (1L << 32) == 4_294_967_296L; // int shifts use count % 32

        // | equals + only when the bits don't overlap. After <<8 the low 8 bits are 0.
        assert ((5L << 8) | 7) == (5L << 8) + 7;
        assert (3 | 1) == 3 && (3 + 1) == 4; // overlapping bit: | doesn't carry

        // & 0xFF equals % 256 for non-negative numbers, but not for negative ones.
        assert (1000 & 0xFF) == 1000 % 256;
        assert (-1 & 0xFF) == 255 && (-1 % 256) == -1;

        // byte is signed (-128..127): 192 = 0b1100_0000 has the top bit set, so it reads as
        // 192 - 256 = -64. & 0xFF widens to int and keeps only the 8 bits: 192 again.
        byte b = (byte) 192;
        assert b == -64;
        assert (b & 0xFF) == 192;

        assert nextSeq(1000, 500) == 1500;
        assert nextSeq(MASK_32, 1) == 0; // 4294967295 + 1 wraps
        assert nextSeq(MASK_32 - 9, 20) == 10;

        System.out.println("all IP math checks passed");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            selfCheck();
            return;
        }
        for (int i = 1; i < args.length; i++) {
            switch (args[0]) {
                case "toLong" -> System.out.println(ipv4ToLong(args[i]));
                case "toIp" -> System.out.println(longToIpv4(Long.parseLong(args[i])));
                case "private" -> System.out.println(isPrivate(args[i]));
                default -> throw new IllegalArgumentException("modes: toLong, toIp, private");
            }
        }
    }
}
