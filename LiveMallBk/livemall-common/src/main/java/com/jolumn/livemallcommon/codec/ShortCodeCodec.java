package com.jolumn.livemallcommon.codec;

/**
 * 短码编解码器 — 基于雪花 ID 的确定性算法
 * <p>
 * 核心契约：ProductId ↔ ShortCode 双向推导，无状态、无存储
 * <p>
 * 算法流程：
 * 1. 取雪花 ID 全 64 位（去掉 MASK_48 的限制，确保双向一致性）
 * 2. Base58 编码（去除易混淆字符 0/O/I/l，最长 11 字符）
 * 3. 添加版本前缀标识业务类型
 * <p>
 * 示例：
 * - 商品 ID: 1876543210987654321
 * - Base58(全 64 位): "48zVYK5Fg8Kp2mN9"
 * - 最终短码: "P48zVYK5Fg8Kp2mN9" (P = Product)
 */
public final class ShortCodeCodec {

    private static final String BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    public static final char PREFIX_PRODUCT = 'P';
    public static final char PREFIX_ACTIVITY = 'A';
    public static final char PREFIX_LIVE = 'L';

    private ShortCodeCodec() {
    }

    public static String encodeProduct(long productId) {
        return encode(productId, PREFIX_PRODUCT);
    }

    public static String encodeActivity(long activityId) {
        return encode(activityId, PREFIX_ACTIVITY);
    }

    public static String encodeLive(long liveId) {
        return encode(liveId, PREFIX_LIVE);
    }

    /** 通用编码 — 全 64 位 Base58，确保 encode(decode(code)) == id */
    public static String encode(long id, char prefix) {
        String encoded = base58Encode(id);
        return prefix + encoded;
    }

    /** 短码 → 原始 ID */
    public static long decode(String shortCode) {
        if (shortCode == null || shortCode.length() < 2) {
            throw new IllegalArgumentException("短码格式无效");
        }
        String encoded = shortCode.substring(1);
        return base58Decode(encoded);
    }

    public static char getPrefix(String shortCode) {
        if (shortCode == null || shortCode.isEmpty()) {
            throw new IllegalArgumentException("短码为空");
        }
        return shortCode.charAt(0);
    }

    public static boolean isProductShortCode(String shortCode) {
        return getPrefix(shortCode) == PREFIX_PRODUCT;
    }

    public static boolean isActivityShortCode(String shortCode) {
        return getPrefix(shortCode) == PREFIX_ACTIVITY;
    }

    public static boolean isLiveShortCode(String shortCode) {
        return getPrefix(shortCode) == PREFIX_LIVE;
    }

    private static String base58Encode(long value) {
        if (value == 0) {
            return String.valueOf(BASE58_ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v > 0) {
            int remainder = (int) (v % 58);
            sb.append(BASE58_ALPHABET.charAt(remainder));
            v /= 58;
        }

        return sb.reverse().toString();
    }

    private static long base58Decode(String encoded) {
        long value = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int digit = BASE58_ALPHABET.indexOf(c);
            if (digit < 0) {
                throw new IllegalArgumentException("无效的 Base58 字符: " + c);
            }
            value = value * 58 + digit;
        }
        return value;
    }
}
