package com.jolumn.livemallwebsocket.push;

import com.jolumn.livemallcommon.api.WsPushService;
import com.jolumn.livemallwebsocket.manager.WsSessionManager;
import com.jolumn.livemallwebsocket.model.WsSession;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@DubboService
@Component
public class WsPushServiceImpl implements WsPushService {

    private static final Logger log = LoggerFactory.getLogger(WsPushServiceImpl.class);

    private final WsSessionManager sessionManager;

    public WsPushServiceImpl(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean push(Long userId, String message) {
        WsSession ws = sessionManager.findByUserId(userId);
        if (ws == null) return false;
        sendRaw(ws, message);
        return true;
    }

    @Override
    public boolean pushToRoom(Long roomId, String message) {
        var sessions = sessionManager.getRoomSessions(roomId);
        if (sessions.isEmpty()) return false;
        for (WsSession ws : sessions) {
            sendRaw(ws, message);
        }
        return true;
    }

    @Override
    public boolean kickUser(Long userId, String reason) {
        WsSession ws = sessionManager.findByUserId(userId);
        if (ws == null) return false;
        kick(ws, reason);
        return true;
    }

    @Override
    public boolean kickDevice(Long userId, String deviceId) {
        // 当前无 ws:route 设备粒度路由，退化为踢该用户所有 session
        // 与 kickUser 行为一致。后续 ws:route 引入 deviceId 维度后再做设备级精确踢
        return kickUser(userId, "DEVICE_KICKED");
    }

    private void kick(WsSession ws, String reason) {
        String msg;
        try {
            msg = mapper.writeValueAsString(Map.of(
                    "type", "KICK",
                    "data", Map.of("reason", reason)));
        } catch (Exception e) {
            log.error("序列化 KICK 消息失败", e);
            return;
        }
        sendRaw(ws, msg);

        // 异步等待 3s 后强制关闭（VT 下 Thread.sleep 不占平台线程）
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (ws.getSession().isOpen()) {
                try {
                    ws.getSession().close();
                } catch (Exception e) {
                    log.warn("kick 后关闭 session 失败: session={}", ws.getSessionId());
                }
            }
        });
    }

    private void sendRaw(WsSession ws, String message) {
        try {
            ws.getSession().getAsyncRemote().sendText(message);
        } catch (Exception e) {
            log.warn("WS 推送失败: session={}, {}", ws.getSessionId(), e.getMessage());
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
}
