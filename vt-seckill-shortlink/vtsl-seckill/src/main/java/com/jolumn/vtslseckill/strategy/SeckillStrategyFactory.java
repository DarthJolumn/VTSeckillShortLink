package com.jolumn.vtslseckill.strategy;

import com.jolumn.vtslseckill.entity.enums.SeckillMode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SeckillStrategyFactory {

    private final Map<SeckillMode, SeckillStrategy> strategyMap;

    public SeckillStrategyFactory(List<SeckillStrategy> strategyList) {
        this.strategyMap = strategyList.stream()
                .collect(Collectors.toMap(
                        this::resolveMode, Function.identity()
                ));
    }

    public SeckillMode resolveMode(SeckillStrategy seckillStrategy) {
        if (seckillStrategy instanceof RedisAsyncStrategy) return SeckillMode.REDIS_ASYNC;
        if (seckillStrategy instanceof RedisSyncStrategy) return SeckillMode.REDIS_SYNC;
        if (seckillStrategy instanceof DBQueueStrategy) return SeckillMode.DB_QUEUE;
        throw new IllegalStateException("未知策略模式:" + seckillStrategy.getClass());
    }

    public SeckillStrategy getStrategy(SeckillMode mode) {
        SeckillStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new IllegalStateException("不支持的秒杀模式: " + mode);
        }
        return strategy;
    }
}
