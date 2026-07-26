package com.jolumn.livemallcommon.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortCodeCodecTest {

    @Test
    void testProductEncodeDecode() {
        long productId = 1876543210987654321L;
        String shortCode = ShortCodeCodec.encodeProduct(productId);

        System.out.println("Product ID: " + productId);
        System.out.println("Short Code: " + shortCode);

        // 验证前缀
        assertEquals('P', ShortCodeCodec.getPrefix(shortCode));
        assertTrue(ShortCodeCodec.isProductShortCode(shortCode));

        // 验证双向推导（全 64 位，无掩码截断）
        long decoded = ShortCodeCodec.decode(shortCode);
        assertEquals(productId, decoded);
    }

    @Test
    void testActivityEncodeDecode() {
        long activityId = 123456789L;
        String shortCode = ShortCodeCodec.encodeActivity(activityId);

        System.out.println("Activity ID: " + activityId);
        System.out.println("Short Code: " + shortCode);

        assertEquals('A', ShortCodeCodec.getPrefix(shortCode));
        assertTrue(ShortCodeCodec.isActivityShortCode(shortCode));
    }

    @Test
    void testLiveEncodeDecode() {
        long liveId = 987654321L;
        String shortCode = ShortCodeCodec.encodeLive(liveId);

        System.out.println("Live ID: " + liveId);
        System.out.println("Short Code: " + shortCode);

        assertEquals('L', ShortCodeCodec.getPrefix(shortCode));
        assertTrue(ShortCodeCodec.isLiveShortCode(shortCode));
    }

    @Test
    void testDecodeInvalidShortCode() {
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode("P"));
        assertThrows(IllegalArgumentException.class, () -> ShortCodeCodec.decode(null));
    }

    @Test
    void testBase58NoConfusingChars() {
        // 验证 Base58 不包含 0, O, I, l
        for (long i = 0; i < 1000; i++) {
            String shortCode = ShortCodeCodec.encodeProduct(i);
            assertFalse(shortCode.contains("0"), "不应包含 0: " + shortCode);
            assertFalse(shortCode.contains("O"), "不应包含 O: " + shortCode);
            assertFalse(shortCode.contains("I"), "不应包含 I: " + shortCode);
            assertFalse(shortCode.contains("l"), "不应包含 l: " + shortCode);
        }
    }

    @Test
    void testRoundTrip() {
        // 测试大量 ID 的双向推导
        for (long i = 1; i <= 10000; i++) {
            long productId = i * 1000000L;
            String shortCode = ShortCodeCodec.encodeProduct(productId);
            long decoded = ShortCodeCodec.decode(shortCode);
            assertEquals(productId, decoded,
                    "Round trip failed for ID: " + productId);
        }
    }
}
