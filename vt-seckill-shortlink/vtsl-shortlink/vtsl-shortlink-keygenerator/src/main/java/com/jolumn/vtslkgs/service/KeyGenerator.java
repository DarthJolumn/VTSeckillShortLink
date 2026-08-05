package com.jolumn.vtslkgs.service;

import com.jolumn.vtslkgs.constant.KgsConstants;
import com.jolumn.vtslkgs.entity.ShortKeyDocument;
import com.jolumn.vtslkgs.util.Base62Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class KeyGenerator {

    private static final Logger log = LoggerFactory.getLogger(KeyGenerator.class);

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;

    public KeyGenerator(MongoTemplate mongoTemplate, StringRedisTemplate redisTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
    }

    /** 启动探针：打印实际连接的 Mongo 库名（排查 shortkeys 误落 test 库问题） */
    @PostConstruct
    public void logMongoDatabase() {
        log.info("KGS 连接 MongoDB 库: '{}'（shortkeys 唯一索引由 auto-index-creation 自动创建）",
                mongoTemplate.getDb().getName());
    }

    public void generateBatch(int count) {
        for (int attempt = 0; attempt < KgsConstants.MAX_GENERATE_ATTEMPTS; attempt++) {
            List<String> keys = new ArrayList<>(count);
            List<ShortKeyDocument> docs = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                String key = Base62Util.randomKey(KgsConstants.KEY_LENGTH);
                keys.add(key);
                docs.add(new ShortKeyDocument(key, KgsConstants.STATUS_AVAILABLE, Instant.now()));
            }

            try {
                mongoTemplate.insert(docs, "shortkeys");
                redisTemplate.opsForList().leftPushAll(KgsConstants.REDIS_QUEUE_NAME, keys.toArray(new String[0]));
                log.info("Generated and queued {} keys", count);
                return;
            } catch (DuplicateKeyException e) {
                log.warn("Duplicate keys in batch, retrying (attempt {}/{})", attempt + 1, KgsConstants.MAX_GENERATE_ATTEMPTS);
            }
        }
        throw new RuntimeException("Failed to generate keys after " + KgsConstants.MAX_GENERATE_ATTEMPTS + " attempts");
    }
}
