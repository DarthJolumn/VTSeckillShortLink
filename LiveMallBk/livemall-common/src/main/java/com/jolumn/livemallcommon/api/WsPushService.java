package com.jolumn.livemallcommon.api;

/**
 * WebSocket 推送服务 Dubbo 接口 — 供其他模块向 WebSocket 节点推送消息。
 * 由 livemall-websocket 模块实现。
 */
public interface WsPushService {

    /**
     * 推送消息给指定用户
     */
    boolean push(Long userId, String message);

    /**
     * 推送消息给房间所有人（含匿名连接）
     */
    boolean pushToRoom(Long roomId, String message);

    /**
     * 强制踢用户下线
     */
    boolean kickUser(Long userId, String reason);

    /**
     * 踢掉指定设备
     */
    boolean kickDevice(Long userId, String deviceId);
}