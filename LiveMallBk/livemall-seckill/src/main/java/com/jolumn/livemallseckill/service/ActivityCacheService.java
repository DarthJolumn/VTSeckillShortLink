package com.jolumn.livemallseckill.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多级缓存: Caffeine L1 + Redis + MySQL。
 * L1 缓存活动信息(5s) + 售罄标记(本地快速拒绝, 减少 90% Redis 查询)。
 * 注意: 售罄标记有最多 5s 延迟, 但 Redis Lua 最终防超卖。
 */
@Service
public class ActivityCacheService {

    private final Cache<Long, SeckillActivity> activityCache;
    private final ConcurrentHashMap<Long, Boolean> soldOutFlags = new ConcurrentHashMap<>();
    private final SeckillActivityRepository activityRepo;

    public ActivityCacheService(SeckillActivityRepository activityRepo,
                                 @Value("${seckill.activity-cache.ttl-seconds:5}") int ttlSeconds,
                                 @Value("${seckill.activity-cache.max-size:1000}") int maxSize) {
        this.activityRepo = activityRepo;
        this.activityCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .build();
    }

    /** Caffeine 缓存活动信息, 5s 过期 */
    public SeckillActivity getActivity(Long activityId) {
        return activityCache.get(activityId,
                id -> activityRepo.findById(id).orElse(null));
    }

    /** 标记售罄 (本地快速拒绝) */
    public void markSoldOut(Long activityId) {
        soldOutFlags.put(activityId, true);
    }

    /** 恢复库存 (回补时调用) */
    public void markInStock(Long activityId) {
        soldOutFlags.remove(activityId);
    }

    /** 快速售罄检查。true=本地标记已售罄, 直接拒绝 */
    public boolean isSoldOut(Long activityId) {
        return Boolean.TRUE.equals(soldOutFlags.get(activityId));
    }

    /** 刷新缓存 */
    public void refresh(Long activityId) {
        activityCache.invalidate(activityId);
    }
}
