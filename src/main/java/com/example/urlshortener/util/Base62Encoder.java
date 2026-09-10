package com.example.urlshortener.util;

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
     * Encodes a non-negative id into a Base62 string.
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
     * Decodes a Base62 string back into its numeric id.
     *
     * @throws IllegalArgumentException if code is null/blank or contains a character outside the Base62 alphabet
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
            result = result * BASE + digit;
        }
        return result;
    }
}
