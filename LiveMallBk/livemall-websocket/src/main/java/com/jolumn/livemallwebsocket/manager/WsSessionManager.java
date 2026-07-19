package com.jolumn.livemallwebsocket.manager;

import com.jolumn.livemallwebsocket.model.WsSession;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionManager {

    private static final Logger log = LoggerFactory.getLogger(WsSessionManager.class);

    private final Map<String, WsSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, WsSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> deviceSessions = new ConcurrentHashMap<>(); // "userId:deviceId" → Set<sessionId>

    public void add(WsSession ws) {
        sessions.put(ws.getSessionId(), ws);
        roomSessions.computeIfAbsent(ws.getRoomId(), k -> new ConcurrentHashMap<>())
                .put(ws.getSessionId(), ws);
        if (ws.getUserId() != null && ws.getDeviceId() != null) {
            String key = ws.getUserId() + ":" + ws.getDeviceId();
            deviceSessions.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                    .add(ws.getSessionId());
        }
        log.debug("Session 加入: id={}, room={}, userId={}, device={}, online={}",
                ws.getSessionId(), ws.getRoomId(), ws.getUserId(), ws.getDeviceId(), getRoomOnline(ws.getRoomId()));
    }

    public WsSession remove(String sessionId) {
        WsSession ws = sessions.remove(sessionId);
        if (ws != null) {
            Map<String, WsSession> room = roomSessions.get(ws.getRoomId());
            if (room != null) {
                room.remove(sessionId);
                if (room.isEmpty()) roomSessions.remove(ws.getRoomId());
            }
            if (ws.getUserId() != null && ws.getDeviceId() != null) {
                String key = ws.getUserId() + ":" + ws.getDeviceId();
                Set<String> set = deviceSessions.get(key);
                if (set != null) {
                    set.remove(sessionId);
                    if (set.isEmpty()) deviceSessions.remove(key);
                }
            }
            log.debug("Session 移除: id={}, room={}, online={}",
                    sessionId, ws.getRoomId(), getRoomOnline(ws.getRoomId()));
        }
        return ws;
    }

    public WsSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    public WsSession getByRaw(Session session) {
        return sessions.get(session.getId());
    }

    public Collection<WsSession> getRoomSessions(Long roomId) {
        Map<String, WsSession> room = roomSessions.get(roomId);
        return room != null ? room.values() : Set.of();
    }

    public int getRoomOnline(Long roomId) {
        Map<String, WsSession> room = roomSessions.get(roomId);
        return room != null ? room.size() : 0;
    }

    /** 查找用户的所有会话（同账号可多个设备/多房间/多tab） */
    public Collection<WsSession> findByUserId(Long userId) {
        return sessions.values().stream()
                .filter(ws -> userId.equals(ws.getUserId()))
                .toList();
    }

    /** 查找用户+设备的所有会话（踢设备时精确关闭） */
    public Collection<WsSession> findByUserIdAndDeviceId(Long userId, String deviceId) {
        String key = userId + ":" + deviceId;
        Set<String> sessionIds = deviceSessions.get(key);
        if (sessionIds == null || sessionIds.isEmpty()) return Set.of();
        return sessionIds.stream()
                .map(sessions::get)
                .filter(ws -> ws != null)
                .toList();
    }

    public int totalOnline() {
        return sessions.size();
    }

    public Collection<WsSession> getAllSessions() {
        return sessions.values();
    }
}