package com.jolumn.vtslleaderboard.controller;

import com.jolumn.vtslcommon.dto.RankEntry;
import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslleaderboard.service.LeaderboardServiceImpl;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/leaderboard")
public class LeaderboardController {

    private final LeaderboardServiceImpl service;

    public LeaderboardController(LeaderboardServiceImpl service) {
        this.service = service;
    }

    /** 加分（压测用 + 模拟礼物/点赞/观看/评论/分享） */
    @PostMapping("/score")
    public Result<Boolean> addScore(@RequestParam Long activityId,
                                    @RequestParam Long userId,
                                    @RequestParam(defaultValue = "WATCH") String eventType) {
        return Result.ok(service.addScore(activityId, userId, eventType));
    }

    /** 实时 TopN */
    @GetMapping("/top")
    public Result<List<RankEntry>> topN(@RequestParam Long activityId,
                                        @RequestParam(defaultValue = "100") int n) {
        return Result.ok(service.getTopN(activityId, n));
    }

    /** 个人排名 */
    @GetMapping("/rank/{userId}")
    public Result<RankEntry> rank(@PathVariable Long userId,
                                  @RequestParam Long activityId) {
        return Result.ok(service.getRank(activityId, userId));
    }

    /** 历史排行 */
    @GetMapping("/history")
    public Result<List<RankEntry>> history(@RequestParam Long activityId,
                                           @RequestParam(required = false) String time) {
        LocalDateTime t = time != null ? LocalDateTime.parse(time) : LocalDateTime.now();
        return Result.ok(service.getHistory(activityId, t));
    }
}
