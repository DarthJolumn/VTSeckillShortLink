package com.jolumn.livemallleaderboard.scheduler;

import com.jolumn.livemallcommon.dto.RankEntry;
import com.jolumn.livemallleaderboard.entity.LeaderboardSnapshot;
import com.jolumn.livemallleaderboard.repository.LeaderboardSnapshotRepository;
import com.jolumn.livemallleaderboard.service.LeaderboardServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final LeaderboardServiceImpl leaderboardService;
    private final LeaderboardSnapshotRepository snapshotRepo;

    public SnapshotScheduler(LeaderboardServiceImpl leaderboardService,
                             LeaderboardSnapshotRepository snapshotRepo) {
        this.leaderboardService = leaderboardService;
        this.snapshotRepo = snapshotRepo;
    }

    /** 每 5 分钟将 Top 100 落库 */
    @Scheduled(cron = "0 */5 * * * ?")
    public void snapshot() {
        LocalDateTime now = LocalDateTime.now();
        log.info("排行榜快照开始: {}", now);

        // 对所有活跃活动做快照（简化实现：遍历所有 ZSet key）
        // 实际应从活动表查活跃活动ID列表
        try {
            List<RankEntry> top100 = leaderboardService.getTopN(1L, 100);
            for (RankEntry entry : top100) {
                LeaderboardSnapshot snap = new LeaderboardSnapshot();
                snap.setActivityId(1L);
                snap.setUserId(entry.getUserId());
                snap.setScore(BigDecimal.valueOf(entry.getScore()));
                snap.setRank(entry.getRank());
                snap.setSnapshotTime(now);
                snapshotRepo.save(snap);
            }
            log.info("排行榜快照完成: 保存 {} 条", top100.size());
        } catch (Exception e) {
            log.warn("排行榜快照失败: {}", e.getMessage());
        }
    }
}
