package com.jolumn.vtslleaderboard.scheduler;

import com.jolumn.vtslcommon.dto.RankEntry;
import com.jolumn.vtslleaderboard.entity.LeaderboardSnapshot;
import com.jolumn.vtslleaderboard.repository.LeaderboardSnapshotRepository;
import com.jolumn.vtslleaderboard.service.LeaderboardServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final LeaderboardServiceImpl leaderboardService;
    private final LeaderboardSnapshotRepository snapshotRepo;
    private final StringRedisTemplate redisTemplate;

    public SnapshotScheduler(LeaderboardServiceImpl leaderboardService,
                             LeaderboardSnapshotRepository snapshotRepo,
                             StringRedisTemplate redisTemplate) {
        this.leaderboardService = leaderboardService;
        this.snapshotRepo = snapshotRepo;
        this.redisTemplate = redisTemplate;
    }

    /** 每 5 分钟扫描所有 ZSet leaderboard:* 并落库 Top 100 */
    @Scheduled(cron = "0 */5 * * * ?")
    public void snapshot() {
        LocalDateTime now = LocalDateTime.now();
        Set<String> keys = redisTemplate.keys("leaderboard:*");
        if (keys == null || keys.isEmpty()) return;

        int totalSaved = 0;
        for (String key : keys) {
            try {
                String idStr = key.substring("leaderboard:".length());
                Long activityId = Long.parseLong(idStr);
                List<RankEntry> top100 = leaderboardService.getTopN(activityId, 100);

                for (RankEntry entry : top100) {
                    LeaderboardSnapshot snap = new LeaderboardSnapshot();
                    snap.setActivityId(activityId);
                    snap.setUserId(entry.getUserId());
                    snap.setScore(BigDecimal.valueOf(entry.getScore()));
                    snap.setRank(entry.getRank());
                    snap.setSnapshotTime(now);
                    snapshotRepo.save(snap);
                }
                totalSaved += top100.size();
            } catch (Exception e) {
                log.warn("快照失败: key={}, error={}", key, e.getMessage());
            }
        }
        log.info("排行榜快照完成: {} 个活动, {} 条记录", keys.size(), totalSaved);
    }
}
