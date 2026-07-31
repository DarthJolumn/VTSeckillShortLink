package com.jolumn.vtslwebsocket.endpoint;

import tools.jackson.databind.ObjectMapper;
import com.jolumn.vtslwebsocket.service.LeaderboardServiceClient;
import com.jolumn.vtslcommon.util.JwtUtil;
import com.jolumn.vtslwebsocket.manager.WsSessionManager;
import com.jolumn.vtslwebsocket.model.WsSession;
import io.jsonwebtoken.Claims;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@ServerEndpoint("/ws/live/{roomId}")
public class LiveWebSocket {

    private static final Logger log = LoggerFactory.getLogger(LiveWebSocket.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static WsSessionManager sessionManager;
    private static JwtUtil jwtUtil;
    private static LeaderboardServiceClient leaderboardServiceClient;
    private static long maxSessions = 200_000;
    private static int maxMessageSize = 64 * 1024;

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") Long roomId) {
        String deviceId = extractParamFromQuery(session.getQueryString(), "deviceId");
        Long userId = null;
        Integer role = null;

        // 连接上限保护
        if (sessionManager.totalOnline() >= maxSessions) {
            log.warn("连接数已达上限: online={}, max={}", sessionManager.totalOnline(), maxSessions);
            sendJson(session, Map.of("type", "ERROR", "data",
                    Map.of("reason", "服务器繁忙，请稍后重试")));
            try { session.close(new jakarta.websocket.CloseReason(
                    jakarta.websocket.CloseReason.CloseCodes.TRY_AGAIN_LATER,
                    "Server at capacity")); } catch (IOException ignored) {}
            return;
        }

        WsSession ws = new WsSession(session, roomId, userId, role, deviceId);
        sessionManager.add(ws);
        session.getUserProperties().put("sessionId", ws.getSessionId());

        if (ws.isAnonymous()) {
            log.info("匿名连接: room={}, session={}", roomId, ws.getSessionId());
        } else {
            log.info("认证连接: room={}, userId={}, session={}", roomId, userId, ws.getSessionId());
        }

        sendJson(session, Map.of("type", "CONNECTED", "data",
                Map.of("anonymous", ws.isAnonymous(),
                        "displayName", ws.getDisplayName(),
                        "online", sessionManager.getRoomOnline(roomId))));
    }

    @OnMessage
    public void onMessage(Session session, String message) {
       WsSession ws = sessionManager.get((String) session.getUserProperties().get("sessionId"));
        if (ws == null) return;
        ws.touch();

        Map<String, Object> msg;
        try {
            msg = mapper.readValue(message, Map.class);
        } catch (Exception e) {
            sendJson(session, Map.of("type", "ERROR", "data", Map.of("reason", "消息格式错误")));
            return;
        }

        String type = (String) msg.get("type");

        if (message.length() > maxMessageSize) {
            sendJson(session, Map.of("type", "ERROR", "data", Map.of("reason", "消息体过大")));
            return;
        }

        if ("PING".equals(type)) {
            sendJson(session, Map.of("type", "PONG", "data", Map.of()));
            return;
        }

        if ("AUTH".equals(type)) {
            handleAuth(ws, session, msg);
            return;
        }

        if (ws.isAnonymous()) {
            sendJson(session, Map.of("type", "NEED_AUTH", "data",
                    Map.of("reason", "请先登录后再操作")));
            return;
        }

        switch (type != null ? type : "") {
            case "BARRAGE" -> handleBarrage(ws, session, msg);
            case "GIFT" -> handleGift(ws, session, msg);
            case "SEC_KILL" -> handleSeckill(ws, session, msg);
            default -> sendJson(session, Map.of("type", "ERROR", "data",
                    Map.of("reason", "未知消息类型: " + type)));
        }
    }

    @SuppressWarnings("unchecked")
    private void handleAuth(WsSession ws, Session session, Map<String, Object> msg) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        if (data == null) {
            sendJson(session, Map.of("type", "AUTH_FAILED", "data", Map.of("reason", "缺少 data")));
            return;
        }
        String token = (String) data.get("token");
        if (token == null || token.isBlank()) {
            sendJson(session, Map.of("type", "AUTH_FAILED", "data", Map.of("reason", "缺少 token")));
            return;
        }

        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = Long.parseLong(claims.getSubject());
            // JJWT + Gson 解析时数字会变成 Double，无法直接转 Integer，需通过 Number 中转
            Integer role = ((Number) claims.get("role")).intValue();
            ws.upgrade(userId, role);
            sessionManager.updateUserIndex(ws);
            log.info("WS 连接升级: session={}, userId={}", ws.getSessionId(), userId);
            sendJson(session, Map.of("type", "AUTH_OK", "data",
                    Map.of("userId", userId, "role", role, "displayName", ws.getDisplayName())));
        } catch (Exception e) {
            log.warn("WS AUTH 升级失败: {}", e.getMessage());
            sendJson(session, Map.of("type", "AUTH_FAILED", "data",
                    Map.of("reason", "Token 无效或已过期")));
        }
    }

    private void handleBarrage(WsSession ws, Session session, Map<String, Object> msg) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        if (data == null) return;
        String content = (String) data.get("content");
        if (content == null || content.isBlank()) return;

        Map<String, Object> broadcast = Map.of(
                "type", "BARRAGE",
                "data", Map.of(
                        "userId", ws.getUserId(),
                        "username", ws.getDisplayName(),
                        "avatar", "",
                        "content", content,
                        "timestamp", System.currentTimeMillis()));

        broadcastToRoom(ws.getRoomId(), broadcast);
    }

    @SuppressWarnings("unchecked")
    private void handleGift(WsSession ws, Session session, Map<String, Object> msg) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        if (data == null) return;
        Object giftId = data.get("giftId");
        Object quantity = data.get("quantity");
        if (giftId == null) return;

        Map<String, Object> broadcast = Map.of(
                "type", "GIFT",
                "data", Map.of(
                        "userId", ws.getUserId(),
                        "username", ws.getDisplayName(),
                        "giftId", giftId,
                        "giftName", "",
                        "giftIcon", "",
                        "price", 0,
                        "gain", 0,
                        "quantity", quantity != null ? quantity : 1,
                        "timestamp", System.currentTimeMillis()));

        broadcastToRoom(ws.getRoomId(), broadcast);

        // Dubbo 调用排行榜加分
        if (leaderboardServiceClient != null) {
            Thread.startVirtualThread(() -> {
                leaderboardServiceClient.addScore(ws.getRoomId(), ws.getUserId(), "GIFT");
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void handleSeckill(WsSession ws, Session session, Map<String, Object> msg) {
        Map<String, Object> data = (Map<String, Object>) msg.get("data");
        if (data == null) return;
        log.info("秒杀请求 via WS: userId={}, activityId={}", ws.getUserId(), data.get("activityId"));
    }

    private void broadcastToRoom(Long roomId, Map<String, Object> message) {
        for (WsSession ws : sessionManager.getRoomSessions(roomId)) {
            sendJson(ws.getSession(), message);
        }
    }

    @OnClose
    public void onClose(Session session) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        if (sessionId != null) {
            WsSession ws = sessionManager.remove(sessionId);
            if (ws != null) {
                log.debug("连接关闭: room={}, session={}, anonymous={}",
                        ws.getRoomId(), sessionId, ws.isAnonymous());
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String sessionId = (String) session.getUserProperties().get("sessionId");
        log.error("WS 错误: session={}, error={}", sessionId, error.getMessage());
    }

    private String extractParamFromQuery(String query, String name) {
        if (query == null || query.isBlank()) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }

    private void sendJson(Session session, Map<String, Object> message) {
        if (!session.isOpen()) return;
        try {
            session.getAsyncRemote().sendText(mapper.writeValueAsString(message));
        } catch (Exception e) {
            log.error("WS 发送失败: {}", e.getMessage());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setSessionManager(WsSessionManager sessionManager) {
        LiveWebSocket.sessionManager = sessionManager;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setJwtUtil(JwtUtil jwtUtil) {
        LiveWebSocket.jwtUtil = jwtUtil;
    }

    @org.springframework.beans.factory.annotation.Value("${ws.max-sessions:200000}")
    public void setMaxSessions(long maxSessions) {
        LiveWebSocket.maxSessions = maxSessions;
    }

    @org.springframework.beans.factory.annotation.Value("${ws.max-message-size:65536}")
    public void setMaxMessageSize(int maxMessageSize) {
        LiveWebSocket.maxMessageSize = maxMessageSize;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLeaderboardServiceClient(LeaderboardServiceClient leaderboardServiceClient) {
        LiveWebSocket.leaderboardServiceClient = leaderboardServiceClient;
    }
}