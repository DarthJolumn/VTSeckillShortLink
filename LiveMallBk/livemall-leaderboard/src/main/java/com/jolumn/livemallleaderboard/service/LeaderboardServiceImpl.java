package com.jolumn.livemallleaderboard.service;

import com.jolumn.livemallcommon.api.LeaderboardService;
import com.jolumn.livemallcommon.dto.RankEntry;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@DubboService
public class LeaderboardServiceImpl implements LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardServiceImpl.class);

    private static final Map<String, Double> WEIGHT_MAP = Map.of(
            "WATCH", 0.3, "LIKE", 0.5, "COMMENT", 1.0, "SHARE", 2.0, "GIFT", 5.0);

    private final StringRedisTemplate redisTemplate;

    public LeaderboardServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean addScore(Long activityId, Long userId, String eventType) {
        double weight = WEIGHT_MAP.getOrDefault(eventType.toUpperCase(), 0.0);
        String key = "leaderboard:" + activityId;
        redisTemplate.opsForZSet().incrementScore(key, userId.toString(), weight);
        log.debug("排行榜加分: activityId={}, userId={}, type={}, weight={}", activityId, userId, eventType, weight);
        return true;
    }

    @Override
    public List<RankEntry> getTopN(Long activityId, int n) {
        String key = "leaderboard:" + activityId;
        Set<ZSetOperations.TypedTuple<String>> set =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);

        if (set == null || set.isEmpty()) return List.of();

        List<RankEntry> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : set) {
            RankEntry entry = new RankEntry();
            entry.setUserId(Long.parseLong(tuple.getValue()));
            entry.setScore(tuple.getScore());
            entry.setRank(rank++);
            result.add(entry);
        }
        return result;
    }

    @Override
    public RankEntry getRank(Long activityId, Long userId) {
        String key = "leaderboard:" + activityId;
        String member = userId.toString();
        Double score = redisTemplate.opsForZSet().score(key, member);
        if (score == null) return new RankEntry(userId, 0.0, -1);

        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        return new RankEntry(userId, score, rank != null ? rank.intValue() + 1 : -1);
    }

    @Override
    public List<RankEntry> getHistory(Long activityId, LocalDateTime time) {
        // 查快照表，暂空实现（依赖快照数据）
        return List.of();
    }
}
