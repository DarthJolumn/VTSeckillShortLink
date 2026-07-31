package com.jolumn.vtslseckill.entity.enums;

public enum SeckillMode {
    REDIS_ASYNC,  // Redis Lua 原子扣减 + Kafka 异步落库
    REDIS_SYNC,   // Redis Lua 原子扣减 + Kafka.get() 同步等待落库
    DB_QUEUE      // DB 乐观锁扣减 + Kafka 顺序消费
}
