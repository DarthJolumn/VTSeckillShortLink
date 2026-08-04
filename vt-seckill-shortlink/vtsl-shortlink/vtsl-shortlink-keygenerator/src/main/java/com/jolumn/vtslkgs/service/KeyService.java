package com.jolumn.vtslkgs.service;

import com.jolumn.vtslkgs.constant.KgsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class KeyService {

    private static final Logger log = LoggerFactory.getLogger(KeyService.class);

    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final KeyGenerator keyGenerator;

    public KeyService(StringRedisTemplate redisTemplate,
                      MongoTemplate mongoTemplate,
                      KeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.keyGenerator = keyGenerator;
    }

    public String getKey() {
        Long queueLen = redisTemplate.opsForList().size(KgsConstants.REDIS_QUEUE_NAME);
        if (queueLen == null) {
            throw new RuntimeException("Redis unavailable");
        }

        if (queueLen < KgsConstants.QUEUE_THRESHOLD) {
            log.info("Queue length {} below threshold {}, generating more keys", queueLen, KgsConstants.QUEUE_THRESHOLD);
            keyGenerator.generateBatch(KgsConstants.BATCH_SIZE);
        }

        String key = redisTemplate.opsForList().rightPop(KgsConstants.REDIS_QUEUE_NAME);
        if (key == null) {
            throw new RuntimeException("Queue empty after refill");
        }

        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("key").is(key)),
                Update.update("status", KgsConstants.STATUS_USED),
                "shortkeys"
        );

        if (result.getModifiedCount() == 0) {
            redisTemplate.opsForList().leftPush(KgsConstants.REDIS_QUEUE_NAME, key);
            throw new RuntimeException("Failed to update key status in DB, pushed key " + key + " back");
        }

        log.debug("Key issued: {}", key);
        return key;
    }
}
