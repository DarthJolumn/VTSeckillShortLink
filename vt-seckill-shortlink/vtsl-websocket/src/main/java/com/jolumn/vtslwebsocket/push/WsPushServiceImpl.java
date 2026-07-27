package com.jolumn.vtslwebsocket.push;

import com.jolumn.vtslcommon.api.WsPushService;
import com.jolumn.vtslwebsocket.manager.WsSessionManager;
import com.jolumn.vtslwebsocket.model.WsSession;

import java.util.Collection;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Semaphore;

@DubboService
@Component
public class WsPushServiceImpl implements WsPushService {

    private static final Logger log = LoggerFactory.getLogger(WsPushServiceImpl.class);
    private static final Semaphore PUSH_SEM = new Semaphore(200);

    private final WsSessionManager sessionManager;

    public WsPushServiceImpl(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean push(Long userId, String message) {
        Collection<WsSession> sessions = sessionManager.findByUserId(userId);
        if (sessions.isEmpty()) return false;
        sessions.forEach(ws -> sendRaw(ws, message));
        return true;
    }

    @Override
    public boolean pushToRoom(Long roomId, String message) {
        var sessions = sessionManager.getRoomSessions(roomId);
        if (sessions.isEmpty()) return false;
        for (WsSession ws : sessions) {
            try {
                PUSH_SEM.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }
            Thread.startVirtualThread(() -> {
                try {
                    sendRaw(ws, message);
                } finally {
                    PUSH_SEM.release();
                }
            });
        }
        return true;
    }

    @Override
    public boolean kickUser(Long userId, String reason) {
        Collection<WsSession> sessions = sessionManager.findByUserId(userId);
        if (sessions.isEmpty()) return false;
        sessions.forEach(ws -> kick(ws, reason));
        return true;
    }

    @Override
    public boolean kickDevice(Long userId, String deviceId) {
        Collection<WsSession> sessions = sessionManager.findByUserIdAndDeviceId(userId, deviceId);
        if (sessions.isEmpty()) return false;
        for (WsSession ws : sessions) {
            kick(ws, "DEVICE_KICKED");
        }
        return true;
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
        if (!ws.getSession().isOpen()) return;
        try {
            ws.getSession().getAsyncRemote().sendText(message);
        } catch (Exception e) {
            log.warn("WS 推送失败: session={}, {}", ws.getSessionId(), e.getMessage());
        }
    }

    private static final tools.jackson.databind.ObjectMapper mapper =
            new tools.jackson.databind.ObjectMapper();
}
