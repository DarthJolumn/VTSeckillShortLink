package com.jolumn.livemallseckill.config;

import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import com.jolumn.livemallseckill.service.ActivityCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SeckillDataWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillDataWarmup.class);

    private final ActivityCacheService cacheService;
    private final SeckillActivityRepository activityRepo;

    public SeckillDataWarmup(ActivityCacheService cacheService,
                             SeckillActivityRepository activityRepo) {
        this.cacheService = cacheService;
        this.activityRepo = activityRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillActivity> activeActivities = activityRepo.findByStatusOrderByStartTimeAsc(1);
        for (SeckillActivity activity : activeActivities) {
            cacheService.put(activity.getId(), activity);
            log.debug("预热活动: id={}", activity.getId());
        }
        log.info("数据预热完成: {} 个活动", activeActivities.size());
    }
}
