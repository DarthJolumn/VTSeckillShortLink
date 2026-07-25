package com.jolumn.livemallseckill.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 多级缓存: Caffeine L1 + MySQL。
 * 活动信息: refreshAfterWrite(5s) + expireAfterAccess(30s) → 过期返回旧值 + 后台异步刷新
 * 注意: 售罄判断由 Redis Lua deduct 返回 -2 兜底，无需本地售罄缓存。
 */
@Service
public class ActivityCacheService {

    private final LoadingCache<Long, SeckillActivity> activityCache;
    private final SeckillActivityRepository activityRepo;

    public ActivityCacheService(SeckillActivityRepository activityRepo,
                                 @Value("${seckill.activity-cache.ttl-seconds:5}") int ttlSeconds,
                                 @Value("${seckill.activity-cache.max-size:1000}") int maxSize) {
        this.activityRepo = activityRepo;
        this.activityCache = Caffeine.newBuilder()
                .refreshAfterWrite(Duration.ofSeconds(ttlSeconds))
                .expireAfterAccess(Duration.ofSeconds(30))
                .maximumSize(maxSize)
                .build(id -> activityRepo.findById(id).orElse(null));
    }

    /** Caffeine LoadingCache 自动加载活动信息。refreshAfterWrite 后台异步刷新，永不阻塞 */
    public SeckillActivity getActivity(Long activityId) {
        return activityCache.get(activityId);
    }

    /** 预热/刷新缓存：从 DB 加载并写入 Caffeine（上架时调用） */
    public void refresh(Long activityId) {
        activityRepo.findById(activityId).ifPresent(a -> activityCache.put(activityId, a));
    }
}
