package com.urlshortener.util;

/**
 * Encodes a numeric ID into a short, URL-safe Base62 string, and decodes it
 * back. Because every ID comes from an auto-increment primary key, encoding
 * is collision-free by construction: two different IDs can never encode to
 * the same string, since the mapping is a straightforward positional-numeral
 * conversion (the same way binary or hex encode a number, just base 62
 * instead of base 2 or 16). The only place a real collision check is needed
 * is for user-supplied custom aliases, which don't go through this encoder
 * at all - see UrlShortenerService for that path.
 */
public final class Base62Encoder {

    private static final String ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = ALPHABET.length(); // 62

    private Base62Encoder() {} // utility class, never instantiated

    public static String encode(long id) {
        if (id == 0) return String.valueOf(ALPHABET.charAt(0));

        StringBuilder sb = new StringBuilder();
        long value = id;
        while (value > 0) {
            int remainder = (int) (value % BASE);
            sb.append(ALPHABET.charAt(remainder));
            value /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long value = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            value = value * BASE + digit;
        }
        return value;
    }
}
