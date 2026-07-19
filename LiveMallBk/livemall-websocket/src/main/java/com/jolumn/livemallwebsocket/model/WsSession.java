package com.jolumn.livemallwebsocket.model;

import jakarta.websocket.Session;

import java.time.LocalDateTime;
import java.util.UUID;

public class WsSession {

    private final String sessionId;
    private final Session session;
    private final Long roomId;
    private Long userId;
    private Integer role;
    private String deviceId;
    private String guestName;
    private final LocalDateTime connectedAt;
    private volatile long lastActivityAt;

    public WsSession(Session session, Long roomId, Long userId, Integer role, String deviceId) {
        this.sessionId = UUID.randomUUID().toString();
        this.session = session;
        this.roomId = roomId;
        this.userId = userId;
        this.role = role;
        this.deviceId = deviceId;
        this.connectedAt = LocalDateTime.now();
        this.lastActivityAt = System.currentTimeMillis();
        if (userId == null) {
            this.guestName = GuestNameGenerator.generate(this.sessionId);
        }
    }

    public boolean isAnonymous() {
        return userId == null;
    }

    public boolean canSend() {
        return !isAnonymous();
    }

    public void upgrade(Long userId, Integer role) {
        this.userId = userId;
        this.role = role;
        this.guestName = null;
    }

    public void touch() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    public String getDisplayName() {
        if (isAnonymous()) return guestName;
        return "user_" + userId;
    }

    public String getSessionId() { return sessionId; }
    public Session getSession() { return session; }
    public Long getRoomId() { return roomId; }
    public Long getUserId() { return userId; }
    public Integer getRole() { return role; }
    public String getDeviceId() { return deviceId; }
    public String getGuestName() { return guestName; }
    public LocalDateTime getConnectedAt() { return connectedAt; }
    public long getLastActivityAt() { return lastActivityAt; }
}