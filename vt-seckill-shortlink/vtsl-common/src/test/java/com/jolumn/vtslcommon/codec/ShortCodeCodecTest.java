package com.jolumn.vtslcommon.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeCodecTest {

    @Test
    void testProductEncodeDecode() {
        long productId = 1876543210987654321L;
        String shortCode = ShortCodeCodec.encodeProduct(productId);

        assertEquals('P', ShortCodeCodec.getPrefix(shortCode));
        assertTrue(ShortCodeCodec.isProductShortCode(shortCode));

        long decoded = ShortCodeCodec.decode(shortCode);
        assertEquals(productId, decoded);
    }

    @Test
    void testActivityEncodeDecode() {
        long activityId = 123456789L;
        String shortCode = ShortCodeCodec.encodeActivity(activityId);

        assertEquals('A', ShortCodeCodec.getPrefix(shortCode));
        assertTrue(ShortCodeCodec.isActivityShortCode(shortCode));

        long decoded = ShortCodeCodec.decode(shortCode);
        assertEquals(activityId, decoded);
    }

    @Test
    void testLiveEncodeDecode() {
        long liveId = 987654321L;
        String shortCode = ShortCodeCodec.encodeLive(liveId);

        assertEquals('L', ShortCodeCodec.getPrefix(shortCode));
        assertTrue(ShortCodeCodec.isLiveShortCode(shortCode));

        long decoded = ShortCodeCodec.decode(shortCode);
        assertEquals(liveId, decoded);
    }

    @Test
    void testDecodeInvalidShortCode() {
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode("P"));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode(null));
    }

    @Test
    void testDecodeInvalidBase58Char() {
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode("P0"));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode("AOI"));
    }

    @Test
    void testPrefixGetters() {
        assertEquals('P', ShortCodeCodec.getPrefix("Pabc123"));
        assertEquals('A', ShortCodeCodec.getPrefix("Axyz789"));
        assertEquals('L', ShortCodeCodec.getPrefix("Ltest001"));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.getPrefix(""));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.getPrefix(null));
    }

    @Test
    void testPrefixCheckers() {
        assertTrue(ShortCodeCodec.isProductShortCode("Pabc"));
        assertFalse(ShortCodeCodec.isProductShortCode("Aabc"));
        assertFalse(ShortCodeCodec.isProductShortCode("Labc"));

        assertTrue(ShortCodeCodec.isActivityShortCode("Aabc"));
        assertFalse(ShortCodeCodec.isActivityShortCode("Pabc"));

        assertTrue(ShortCodeCodec.isLiveShortCode("Labc"));
        assertFalse(ShortCodeCodec.isLiveShortCode("Pabc"));
    }

    @Test
    void testBase58NoConfusingChars() {
        for (long i = 0; i < 1000; i++) {
            String shortCode = ShortCodeCodec.encodeProduct(i);
            assertFalse(shortCode.contains("0"), "should not contain 0: " + shortCode);
            assertFalse(shortCode.contains("O"), "should not contain O: " + shortCode);
            assertFalse(shortCode.contains("I"), "should not contain I: " + shortCode);
            assertFalse(shortCode.contains("l"), "should not contain l: " + shortCode);
        }
    }

    @Test
    void testEncodeZero() {
        String shortCode = ShortCodeCodec.encodeProduct(0L);
        assertTrue(shortCode.length() >= 2);
        assertEquals('P', shortCode.charAt(0));
        long decoded = ShortCodeCodec.decode(shortCode);
        assertEquals(0L, decoded);
    }

    @Test
    void testEncodeMaxLong() {
        long productId = Long.MAX_VALUE;
        String shortCode = ShortCodeCodec.encodeProduct(productId);
        long decoded = ShortCodeCodec.decode(shortCode);
        assertEquals(productId, decoded);
    }

    @Test
    void testEncodeLargeValues() {
        long[] ids = {
            202607010001001L,
            207032394450997248L,
            0x7FFFFFFFFFFFFFFFL,
            0x0000FFFFFFFFFFFFL,
        };
        for (long id : ids) {
            String shortCode = ShortCodeCodec.encodeProduct(id);
            long decoded = ShortCodeCodec.decode(shortCode);
            assertEquals(id, decoded, "Round trip failed for: " + id);
        }
    }

    @Test
    void testRoundTrip() {
        for (long i = 1; i <= 10000; i++) {
            long productId = i * 1000000L;
            String shortCode = ShortCodeCodec.encodeProduct(productId);
            long decoded = ShortCodeCodec.decode(shortCode);
            assertEquals(productId, decoded, "Round trip failed for ID: " + productId);
        }
    }

    @Test
    void testShortCodeLength() {
        for (long i = 1; i <= 10000; i++) {
            String shortCode = ShortCodeCodec.encodeProduct(i * 1000000L);
            assertTrue(shortCode.length() <= 12,
                    "Short code too long (" + shortCode.length() + "): " + shortCode);
        }
    }
}
