package com.parvez.urlshortener.util;

/**
 * Stateless Base62 encoder/decoder used to turn a numeric primary key into a
 * short, URL-safe code and back. See docs/ARCHITECTURE.md §5 for the
 * design trade-off notes (not a security control).
 */
public final class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
    }

    /**
     * Encodes a non-negative id using 0-9, A-Z, then a-z, with zero encoded as "0".
     *
     * @throws IllegalArgumentException if id is negative
     */
    public static String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative: " + id);
        }
        if (id == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            int remainder = (int) (value % BASE);
            sb.append(ALPHABET.charAt(remainder));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string into a non-negative long, accepting leading zeroes.
     *
     * @throws IllegalArgumentException if code is null/blank, contains a character outside
     *         the Base62 alphabet, or represents a value greater than Long.MAX_VALUE
     */
    public static long decode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        long result = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("invalid Base62 character: " + c);
            }
            if (result > (Long.MAX_VALUE - digit) / BASE) {
                throw new IllegalArgumentException("Base62 value exceeds Long.MAX_VALUE");
            }
            result = result * BASE + digit;
        }
        return result;
    }
}
