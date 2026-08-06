package com.jolumn.vtslseckill.entity.enums;

public enum SeckillMode {
    REDIS_ASYNC,  // Redis Lua 原子扣减 + Kafka 异步落库（消费端落库）
    REDIS_SYNC,   // Redis Lua 原子扣减 + 请求内同步 DB 落库（不经 MQ）
    DB_QUEUE      // DB 乐观锁扣减（@Version CAS）+ Kafka 顺序消费（key=activityId 保序）
}
