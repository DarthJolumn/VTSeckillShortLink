package com.jolumn.vtslshortlink.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdGenerator {

    private static final String SEQUENCE_KEY = "short_url:seq";

    private final StringRedisTemplate redisTemplate;
    private final String alphabet;
    private final int codeLength;

    public IdGenerator(StringRedisTemplate redisTemplate,
                       @Value("${shortlink.code-alphabet:23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz}") String alphabet,
                       @Value("${shortlink.code-length:6}") int codeLength) {
        this.redisTemplate = redisTemplate;
        this.alphabet = alphabet;
        this.codeLength = codeLength;
    }

    /**
     * 生成下一个短码
     * @return Base62 编码的短码
     */
    public String nextCode() {
        Long id = redisTemplate.opsForValue().increment(SEQUENCE_KEY);
        return encode(id);
    }

    /**
     * Base62 编码
     * @param id Redis INCR 生成的递增 ID
     * @return Base62 编码字符串
     */
    private String encode(Long id) {
        StringBuilder sb = new StringBuilder();
        int base = alphabet.length();
        
        while (id > 0) {
            int remainder = (int) (id % base);
            sb.append(alphabet.charAt(remainder));
            id /= base;
        }
        
        // 补齐到固定长度
        while (sb.length() < codeLength) {
            sb.append(alphabet.charAt(0));
        }
        
        return sb.toString();
    }
}
