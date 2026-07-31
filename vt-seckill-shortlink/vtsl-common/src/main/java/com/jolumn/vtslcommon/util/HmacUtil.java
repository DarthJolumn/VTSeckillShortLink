package com.jolumn.vtslcommon.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

public class HmacUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private static final ConcurrentHashMap<String, String> SECRETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> PREV_SECRETS = new ConcurrentHashMap<>();

    public static void setSecret(String appKey, String secret) {
        SECRETS.put(appKey, secret);
    }

    public static void setPreviousSecret(String appKey, String secret) {
        PREV_SECRETS.put(appKey, secret);
    }

    public static String sha256(String data, String appKey) {
        String secret = SECRETS.get(appKey);
        if (secret == null) {
            throw new RuntimeException("HMAC-SHA256 签名失败: 未找到 appKey=" + appKey);
        }
        return sign(data, secret);
    }

    public static boolean verify(String data, String appKey, String sign) {
        String secret = SECRETS.get(appKey);
        if (secret != null && isMatch(data, secret, sign)) {
            return true;
        }
        String prevSecret = PREV_SECRETS.get(appKey);
        return prevSecret != null && isMatch(data, prevSecret, sign);
    }

    private static boolean isMatch(String data, String secret, String signValue) {
        String expected = sign(data, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signValue.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
