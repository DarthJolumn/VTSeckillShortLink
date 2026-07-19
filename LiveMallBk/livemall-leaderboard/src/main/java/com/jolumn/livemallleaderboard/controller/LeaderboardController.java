package com.jolumn.livemallleaderboard.controller;

import com.jolumn.livemallcommon.dto.RankEntry;
import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallleaderboard.service.LeaderboardServiceImpl;
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
