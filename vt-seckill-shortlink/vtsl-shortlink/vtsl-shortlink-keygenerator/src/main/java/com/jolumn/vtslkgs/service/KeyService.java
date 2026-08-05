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

        // CAS 更新：仅当 key 仍为 available 时才置为 used。
        // 若此前出现过"标记超时但实际已成功"，key 已是 used → modifiedCount=0。
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("key").is(key).and("status").is(KgsConstants.STATUS_AVAILABLE)),
                Update.update("status", KgsConstants.STATUS_USED),
                "shortkeys"
        );

        if (result.getModifiedCount() == 0) {
            // 不再 push 回队列：已 USED 的脏 key 若回队会陷入"取出→CAS 失败→回队"死循环。
            // 丢弃该 key（随机生成成本极低），调用方重试会取队列中的下一个 key。
            throw new RuntimeException("Failed to CAS key status, discarded key " + key);
        }

        log.debug("Key issued: {}", key);
        return key;
    }
}
