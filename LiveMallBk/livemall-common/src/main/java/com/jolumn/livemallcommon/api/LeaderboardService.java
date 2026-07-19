package com.jolumn.livemallcommon.api;

import com.jolumn.livemallcommon.dto.RankEntry;

import java.util.List;

/** 排行榜服务 Dubbo 接口 — 由 livemall-leaderboard 模块实现 */
public interface LeaderboardService {

    /** 互动加分（送礼/点赞/评论/分享） */
    boolean addScore(Long activityId, Long userId, String eventType);

    /** 实时 TopN */
    List<RankEntry> getTopN(Long activityId, int n);

    /** 个人排名 */
    RankEntry getRank(Long activityId, Long userId);

    /** 历史排行（查快照表） */
    List<RankEntry> getHistory(Long activityId, java.time.LocalDateTime time);
}
