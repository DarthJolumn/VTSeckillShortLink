package com.jolumn.livemallseckill.config;

import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.service.ActivityBloomFilter;
import com.jolumn.livemallseckill.service.ActivityCacheService;
import com.jolumn.livemallseckill.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀数据预热（启动时执行）
 * 1. 重建布隆过滤器
 * 2. 预加载进行中的活动到 Caffeine
 */
@Component
public class SeckillDataWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillDataWarmup.class);

    private final ActivityBloomFilter bloomFilter;
    private final ActivityCacheService cacheService;
    private final StockService stockService;
    private final com.jolumn.livemallseckill.repository.SeckillActivityRepository activityRepo;

    public SeckillDataWarmup(ActivityBloomFilter bloomFilter,
                             ActivityCacheService cacheService,
                             StockService stockService,
                             com.jolumn.livemallseckill.repository.SeckillActivityRepository activityRepo) {
        this.bloomFilter = bloomFilter;
        this.cacheService = cacheService;
        this.stockService = stockService;
        this.activityRepo = activityRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 1. 重建布隆过滤器（从 DB 加载所有活动 ID）
        bloomFilter.rebuild();

        // 2. 预加载进行中的活动到 Caffeine
        List<SeckillActivity> activeActivities = activityRepo.findByStatusOrderByStartTimeAsc(1);
        for (SeckillActivity activity : activeActivities) {
            cacheService.refresh(activity.getId());
            log.debug("预热活动: id={}, title={}", activity.getId(), activity.getTitle());
        }

        log.info("数据预热完成: {} 个活动, 布隆过滤器已重建", activeActivities.size());
    }
}
