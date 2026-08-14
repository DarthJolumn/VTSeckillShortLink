package com.jolumn.vtslseckill.strategy;

import com.jolumn.vtslseckill.biz.service.strategy.*;
import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import com.jolumn.vtslseckill.model.enums.SeckillMode;
import com.jolumn.vtslseckill.model.enums.SendOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillStrategyFactoryTest {

    private SeckillStrategyFactory factory;

    @Mock private com.jolumn.vtslseckill.biz.mq.KafkaOrderSender sender;
    @Mock private com.jolumn.vtslseckill.biz.mq.SyncDegradeController degrade;

    @BeforeEach
    void setUp() {
        // 用真实策略实例（工厂 resolveMode 用 instanceof 具体类，匿名类无法识别）
        RedisAsyncStrategy redisAsync = new RedisAsyncStrategy(null, sender);
        RedisSyncStrategy redisSync = new RedisSyncStrategy(null, sender, degrade);
        DBQueueStrategy dbQueue = new DBQueueStrategy(null, null);
        factory = new SeckillStrategyFactory(List.of(redisAsync, redisSync, dbQueue));
    }

    @Test
    void shouldReturnRedisAsyncForRedisAsyncMode() {
        assertThat(factory.getStrategy(SeckillMode.REDIS_ASYNC))
                .isInstanceOf(RedisAsyncStrategy.class);
    }

    @Test
    void shouldReturnRedisSyncForRedisSyncMode() {
        assertThat(factory.getStrategy(SeckillMode.REDIS_SYNC))
                .isInstanceOf(RedisSyncStrategy.class);
    }

    @Test
    void shouldReturnDbQueueForDbQueueMode() {
        assertThat(factory.getStrategy(SeckillMode.DB_QUEUE))
                .isInstanceOf(DBQueueStrategy.class);
    }

    @Test
    void shouldThrowForUnsupportedMode() {
        assertThatThrownBy(() -> factory.getStrategy(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不支持的秒杀模式");
    }
}