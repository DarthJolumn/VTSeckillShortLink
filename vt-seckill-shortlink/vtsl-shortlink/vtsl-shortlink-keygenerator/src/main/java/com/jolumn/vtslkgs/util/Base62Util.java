package com.jolumn.vtslkgs.util;

import java.math.BigInteger;
import java.security.SecureRandom;

public final class Base62Util {

    private static final String CHARSET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final BigInteger BASE = BigInteger.valueOf(62);
    private static final SecureRandom RANDOM = new SecureRandom();

    private Base62Util() {}

    public static String randomKey(int length) {
        BigInteger max = BASE.pow(length);
        BigInteger n = new BigInteger(max.bitLength(), RANDOM).mod(max);

        StringBuilder sb = new StringBuilder();
        while (n.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] div = n.divideAndRemainder(BASE);
            sb.append(CHARSET.charAt(div[1].intValue()));
            n = div[0];
        }
        while (sb.length() < length) {
            sb.append(CHARSET.charAt(0));
        }
        return sb.reverse().toString();
    }
}
