package com.jolumn.vtslseckill.strategy;

import com.jolumn.vtslseckill.entity.enums.SeckillMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SeckillStrategyFactoryTest {

    private SeckillStrategyFactory factory;

    @BeforeEach
    void setUp() {
        SeckillStrategy redisAsync = new SeckillStrategy() {
            @Override public int deductStock(Long a, Long b) { return 200; }
            @Override public void createOrder(com.jolumn.vtslseckill.entity.SeckillActivity a, Long b, String c) {}
            @Override public void refundStock(Long a, Long b) {}
        };
        SeckillStrategy redisSync = new SeckillStrategy() {
            @Override public int deductStock(Long a, Long b) { return 200; }
            @Override public void createOrder(com.jolumn.vtslseckill.entity.SeckillActivity a, Long b, String c) {}
            @Override public void refundStock(Long a, Long b) {}
        };
        SeckillStrategy dbQueue = new SeckillStrategy() {
            @Override public int deductStock(Long a, Long b) { return 200; }
            @Override public void createOrder(com.jolumn.vtslseckill.entity.SeckillActivity a, Long b, String c) {}
            @Override public void refundStock(Long a, Long b) {}
        };
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