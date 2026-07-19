package com.jolumn.livemallwebsocket.scheduler;

import com.jolumn.livemallwebsocket.manager.WsSessionManager;
import com.jolumn.livemallwebsocket.model.WsSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 心跳扫描器 — 清理 TCP 半开导致的幽灵连接。
 * 客户端 30s PING，服务端 60s 超时关闭。
 */
@Component
public class HeartbeatScanner {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScanner.class);
    private static final long HEARTBEAT_TIMEOUT_MS = 60_000;

    private final WsSessionManager sessionManager;

    public HeartbeatScanner(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Scheduled(fixedDelay = 10_000)
    public void scan() {
        long now = System.currentTimeMillis();
        Collection<WsSession> all = new ArrayList<>(sessionManager.getAllSessions());
        int closed = 0;

        for (WsSession ws : all) {
            if (now - ws.getLastActivityAt() > HEARTBEAT_TIMEOUT_MS) {
                try {
                    if (ws.getSession().isOpen()) {
                        ws.getSession().close();
                        closed++;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (closed > 0) {
            log.info("心跳扫描: 关闭 {} 个超时连接, 在线 {}", closed, sessionManager.totalOnline());
        }
    }
}
