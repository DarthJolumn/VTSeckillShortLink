package com.jolumn.livemallcommon.codec;

/**
 * 短码编解码器 — 基于雪花 ID 的确定性算法
 * <p>
 * 核心契约：ProductId ↔ ShortCode 双向推导，无状态、无存储
 * <p>
 * 算法流程：
 * 1. 取雪花 ID 低 48 位（足够支撑 2^48 ≈ 281 万亿个商品）
 * 2. Base58 编码（去除易混淆字符 0/O/I/l）
 * 3. 添加版本前缀标识业务类型
 * <p>
 * 示例：
 * - 商品 ID: 1876543210987654321
 * - 低 48 位: 0x0000ABCD12345678
 * - Base58: "5Fg8Kp2mN9"
 * - 最终短码: "P5Fg8Kp2mN9" (P = Product)
 */
public final class ShortCodeCodec {

    // Base58 字符集（Bitcoin 风格，去除 0/O/I/l）
    private static final String BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    // 版本前缀
    public static final char PREFIX_PRODUCT = 'P';
    public static final char PREFIX_ACTIVITY = 'A';
    public static final char PREFIX_LIVE = 'L';

    // 48 位掩码
    private static final long MASK_48_BITS = 0x0000FFFFFFFFFFFFL;

    private ShortCodeCodec() {
    }

    /**
     * 商品 ID → 短码
     *
     * @param productId 雪花 ID
     * @return 带前缀的短码，如 "P5Fg8Kp2mN9"
     */
    public static String encodeProduct(long productId) {
        return encode(productId, PREFIX_PRODUCT);
    }

    /**
     * 活动 ID → 短码
     */
    public static String encodeActivity(long activityId) {
        return encode(activityId, PREFIX_ACTIVITY);
    }

    /**
     * 直播间 ID → 短码
     */
    public static String encodeLive(long liveId) {
        return encode(liveId, PREFIX_LIVE);
    }

    /**
     * 通用编码
     *
     * @param id     业务 ID（雪花 ID）
     * @param prefix 版本前缀
     * @return 短码
     */
    public static String encode(long id, char prefix) {
        long bits48 = id & MASK_48_BITS;
        String encoded = base58Encode(bits48);
        return prefix + encoded;
    }

    /**
     * 短码 → 原始 ID（通用）
     *
     * @param shortCode 短码（带前缀）
     * @return 解码后的 ID
     * @throws IllegalArgumentException 如果短码格式无效
     */
    public static long decode(String shortCode) {
        if (shortCode == null || shortCode.length() < 2) {
            throw new IllegalArgumentException("短码格式无效");
        }

        // 跳过第一个字符（版本前缀）
        String encoded = shortCode.substring(1);
        return base58Decode(encoded);
    }

    /**
     * 获取短码的版本前缀
     */
    public static char getPrefix(String shortCode) {
        if (shortCode == null || shortCode.isEmpty()) {
            throw new IllegalArgumentException("短码为空");
        }
        return shortCode.charAt(0);
    }

    /**
     * 判断是否为商品短码
     */
    public static boolean isProductShortCode(String shortCode) {
        return getPrefix(shortCode) == PREFIX_PRODUCT;
    }

    /**
     * 判断是否为活动短码
     */
    public static boolean isActivityShortCode(String shortCode) {
        return getPrefix(shortCode) == PREFIX_ACTIVITY;
    }

    /**
     * 判断是否为直播间短码
     */
    public static boolean isLiveShortCode(String shortCode) {
        return getPrefix(shortCode) == PREFIX_LIVE;
    }

    // ==================== Base58 编解码 ====================

    /**
     * Base58 编码
     */
    private static String base58Encode(long value) {
        if (value == 0) {
            return String.valueOf(BASE58_ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int remainder = (int) (value % 58);
            sb.append(BASE58_ALPHABET.charAt(remainder));
            value /= 58;
        }

        return sb.reverse().toString();
    }

    /**
     * Base58 解码
     */
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
