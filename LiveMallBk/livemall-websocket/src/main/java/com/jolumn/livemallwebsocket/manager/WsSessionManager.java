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

    public void add(WsSession ws) {
        sessions.put(ws.getSessionId(), ws);
        roomSessions.computeIfAbsent(ws.getRoomId(), k -> new ConcurrentHashMap<>())
                .put(ws.getSessionId(), ws);
        log.debug("Session 加入: id={}, room={}, anonymous={}, online={}",
                ws.getSessionId(), ws.getRoomId(), ws.isAnonymous(), getRoomOnline(ws.getRoomId()));
    }

    public WsSession remove(String sessionId) {
        WsSession ws = sessions.remove(sessionId);
        if (ws != null) {
            Map<String, WsSession> room = roomSessions.get(ws.getRoomId());
            if (room != null) {
                room.remove(sessionId);
                if (room.isEmpty()) roomSessions.remove(ws.getRoomId());
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

    public WsSession findByUserId(Long userId) {
        for (WsSession ws : sessions.values()) {
            if (userId.equals(ws.getUserId())) {
                return ws;
            }
        }
        return null;
    }

    public int totalOnline() {
        return sessions.size();
    }
}