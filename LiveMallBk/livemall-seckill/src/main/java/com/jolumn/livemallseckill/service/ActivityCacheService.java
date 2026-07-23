package com.jolumn.livemallseckill.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 多级缓存: Caffeine L1 + Redis + MySQL。
 * 活动信息: refreshAfterWrite(5s) + expireAfterAccess(30s) → 过期返回旧值 + 后台异步刷新
 * 售罄标记: expireAfterWrite(1s) → 自动过期，多节点弱一致
 * 注意: 售罄标记有最多 1s 延迟, 但 Redis Lua 最终防超卖。
 */
@Service
public class ActivityCacheService {

    private final LoadingCache<Long, SeckillActivity> activityCache;
    private final Cache<Long, Boolean> soldOutCache;
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
        this.soldOutCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(1))
                .maximumSize(maxSize)
                .build();
    }

    /** Caffeine LoadingCache 自动加载活动信息。refreshAfterWrite 后台异步刷新，永不阻塞 */
    public SeckillActivity getActivity(Long activityId) {
        return activityCache.get(activityId);
    }

    /** 标记售罄 (1s TTL, 自动过期) */
    public void markSoldOut(Long activityId) {
        soldOutCache.put(activityId, Boolean.TRUE);
    }

    /** 恢复库存 (回补时清除标记) */
    public void markInStock(Long activityId) {
        soldOutCache.invalidate(activityId);
    }

    /** 快速售罄检查。true=本地标记已售罄, 直接拒绝 */
    public boolean isSoldOut(Long activityId) {
        return Boolean.TRUE.equals(soldOutCache.getIfPresent(activityId));
    }

    /** 预热/刷新缓存：从 DB 加载并写入 Caffeine（上架时调用） */
    public void refresh(Long activityId) {
        activityRepo.findById(activityId).ifPresent(a -> activityCache.put(activityId, a));
    }
}
