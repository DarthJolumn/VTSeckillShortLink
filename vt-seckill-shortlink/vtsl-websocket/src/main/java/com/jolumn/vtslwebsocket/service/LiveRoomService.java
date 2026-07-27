package com.jolumn.vtslwebsocket.service;

import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslwebsocket.entity.LiveRoom;
import com.jolumn.vtslwebsocket.manager.WsSessionManager;
import com.jolumn.vtslwebsocket.mapper.LiveRoomRepository;
import com.jolumn.vtslwebsocket.model.WsSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LiveRoomService {

    private static final Logger log = LoggerFactory.getLogger(LiveRoomService.class);

    private final LiveRoomRepository repo;
    private final WsSessionManager sessionManager;

    public LiveRoomService(LiveRoomRepository repo, WsSessionManager sessionManager) {
        this.repo = repo;
        this.sessionManager = sessionManager;
    }

    /**
     * 开播。幂等：如果该主播已有进行中的直播，直接返回已有房间。
     */
    @Transactional
    public LiveRoom start(Long anchorId, String anchorName, String title,
                          String category, String coverColor) {
        // 幂等：已有直播中房间则复用
        Optional<LiveRoom> existing = repo.findByAnchorIdAndStatus(anchorId, 1);
        if (existing.isPresent()) {
            log.info("主播已有直播中房间，复用: roomId={}", existing.get().getId());
            return existing.get();
        }

        LiveRoom room = new LiveRoom();
        room.setAnchorId(anchorId);
        room.setAnchorName(anchorName);
        room.setTitle(title);
        room.setCategory(category != null ? category : "other");
        room.setCoverColor(coverColor);
        room.setStatus(1);
        room.setOnlineCount(0);
        room.setStartedAt(LocalDateTime.now());
        return repo.save(room);
    }

    /**
     * 关播。仅主播本人可关。
     * 广播 ROOM_CLOSED → 异步关闭所有观众 WS 连接。
     */
    @Transactional
    public void stop(Long roomId, Long anchorId) {
        LiveRoom room = repo.findById(roomId)
                .orElseThrow(() -> new BizException(404, "直播间不存在"));
        if (!room.getAnchorId().equals(anchorId)) {
            throw new BizException(403, "无权操作");
        }
        if (room.getStatus() == 0) {
            return; // 已关播，幂等
        }
        room.setStatus(0);
        room.setEndedAt(LocalDateTime.now());
        room.setOnlineCount(sessionManager.getRoomOnline(roomId));
        repo.save(room);
        log.info("关播成功: roomId={}, anchorId={}", roomId, anchorId);

        // 广播 ROOM_CLOSED + 3s 后关闭所有观众 WS 连接
        String closeMsg = "{\"type\":\"ROOM_CLOSED\",\"data\":{\"roomId\":" + roomId + "}}";
        Thread.startVirtualThread(() -> {
            for (WsSession ws : sessionManager.getRoomSessions(roomId)) {
                try {
                    if (ws.getSession().isOpen()) {
                        ws.getSession().getAsyncRemote().sendText(closeMsg);
                    }
                } catch (Exception e) {
                    log.warn("发送 ROOM_CLOSED 失败: session={}", ws.getSessionId());
                }
            }
            // 等 3s 让客户端收到消息并渲染弹窗
            try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            for (WsSession ws : sessionManager.getRoomSessions(roomId)) {
                try {
                    if (ws.getSession().isOpen()) ws.getSession().close();
                } catch (IOException e) {
                    log.warn("关闭 session 失败: session={}", ws.getSessionId());
                }
            }
        });
    }

    /**
     * 按 ID 查询直播间
     */
    /**
     * 查主播当前活跃的直播间，无则返回 empty
     */
    public Optional<LiveRoom> findByAnchor(Long anchorId) {
        return repo.findByAnchorIdAndStatus(anchorId, 1);
    }

    public LiveRoom findById(Long roomId) {
        return repo.findById(roomId)
                .orElseThrow(() -> new BizException(404, "直播间不存在"));
    }

    /**
     * 获取直播中房间列表（附带实时在线人数）
     */
    public List<LiveRoom> listLiveRooms() {
        List<LiveRoom> rooms = repo.findByStatusOrderByStartedAtDesc(1);
        // 用 WsSessionManager 的实时在线人数覆盖 DB 缓存值
        for (LiveRoom room : rooms) {
            int realOnline = sessionManager.getRoomOnline(room.getId());
            room.setOnlineCount(realOnline);
        }
        return rooms;
    }
}
