package com.jolumn.livemallleaderboard;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * livemall-leaderboard — 排行榜服务（端口 8084）.
 *
 * <p>承担功能点：
 * <ul>
 *   <li>3.5.1 互动加分（Dubbo {@code addScore}）</li>
 *   <li>3.5.2 实时 TopN（HTTP {@code GET /leaderboard/top}）</li>
 *   <li>3.5.3 个人排名（HTTP {@code GET /leaderboard/rank/{userId}}）</li>
 *   <li>3.5.4 定时快照（@Scheduled 每 5min）</li>
 *   <li>3.5.5 历史排行（HTTP {@code GET /leaderboard/history}）</li>
 * </ul>
 *
 * <h3>VT 纪律</h3>
 * <ul>
 *   <li>HTTP 接口走 VT（{@code spring.threads.virtual.enabled=true}）</li>
 *   <li>Redis ZINCRBY / ZREVRANGE / ZSCORE IO 在 VT 上 park</li>
 *   <li>SnapshotTask 的 @Scheduled 跑平台线程，每 5min 一次量小可接受</li>
 *   <li>TopN / 个人排名 / 历史 不加 @Transactional（无 DB 写或单条 SELECT）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.livemallleaderboard",
        "com.jolumn.livemallcommon.util",
        "com.jolumn.livemallcommon.exception",
        "com.jolumn.livemallcommon.dto"
})
@EnableDubbo
@EnableScheduling
public class LivemallLeaderboardApplication {

    static void main(String[] args) {
        SpringApplication.run(LivemallLeaderboardApplication.class, args);
    }
}
