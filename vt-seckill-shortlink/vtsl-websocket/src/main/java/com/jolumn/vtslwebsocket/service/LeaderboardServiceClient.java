package com.jolumn.vtslwebsocket.service;

import com.jolumn.vtslcommon.api.LeaderboardService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通过 Dubbo RPC 调用排行榜服务加分。
 * 送礼/点赞/观看/评论/分享 都会触发 addScore。
 */
@Service
public class LeaderboardServiceClient {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardServiceClient.class);

    @DubboReference(check = false)
    private LeaderboardService leaderboardService;

    public boolean addScore(Long activityId, Long userId, String eventType) {
        try {
            return leaderboardService.addScore(activityId, userId, eventType);
        } catch (Exception e) {
            log.warn("Dubbo 调用排行榜加分失败: activityId={}, userId={}, type={}, error={}",
                    activityId, userId, eventType, e.getMessage());
            return false;
        }
    }
}
