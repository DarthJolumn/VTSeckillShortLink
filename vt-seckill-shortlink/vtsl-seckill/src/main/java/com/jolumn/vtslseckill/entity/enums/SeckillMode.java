package com.jolumn.vtslseckill.entity.enums;

public enum SeckillMode {
    REDIS_ASYNC,  // Redis Lua 原子扣减 + Kafka 异步落库（send() 不等待，消费端落库）
    REDIS_SYNC,   // Redis Lua 原子扣减 + Kafka 同步发送（send().get() 等 broker 确认）
    DB_QUEUE      // DB 乐观锁扣减（@Version CAS）+ Kafka 顺序消费（key=activityId 保序）
}
