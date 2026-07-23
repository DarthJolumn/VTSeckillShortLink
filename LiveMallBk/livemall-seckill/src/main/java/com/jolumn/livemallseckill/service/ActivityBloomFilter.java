package com.jolumn.livemallseckill.service;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ActivityBloomFilter {

    private static final Logger log = LoggerFactory.getLogger(ActivityBloomFilter.class);
    private static final double FPP = 0.01;
    private static final long MIN_SIZE = 100;

    private final AtomicReference<BloomFilter<Long>> filterRef = new AtomicReference<>();
    private final SeckillActivityRepository activityRepo;

    public ActivityBloomFilter(SeckillActivityRepository activityRepo) {
        this.activityRepo = activityRepo;
    }

    @PostConstruct
    public void init() {
        rebuild();
    }

    @Scheduled(fixedDelay = 60_000)
    public void rebuild() {
        List<Long> ids = activityRepo.findActiveIds();
        BloomFilter<Long> newFilter = BloomFilter.create(
                Funnels.longFunnel(), Math.max(ids.size(), MIN_SIZE), FPP);
        for (Long id : ids) {
            newFilter.put(id);
        }
        filterRef.set(newFilter);
        log.info("布隆过滤器重建完成: {} 个进行中活动 ID, 误判率={}", ids.size(), FPP);
    }

    /** 上架时即时插入，不必等 60s 重建窗口 */
    public void add(Long activityId) {
        BloomFilter<Long> f = filterRef.get();
        if (f != null) f.put(activityId);
    }

    public boolean mightContain(Long activityId) {
        BloomFilter<Long> f = filterRef.get();
        return f != null && f.mightContain(activityId);
    }
}
