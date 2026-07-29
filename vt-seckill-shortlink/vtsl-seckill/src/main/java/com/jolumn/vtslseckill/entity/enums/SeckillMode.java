package com.jolumn.vtslseckill.entity.enums;

public enum SeckillMode {
    REDIS_ASYNC,  // Redis Lua + Kafka 异步
    REDIS_SYNC,   // Redis Lua + Kafka.get() 同步等待

    DB_QUEUE,     // 直接 Kafka 顺序 DB 乐观锁扣减
}
