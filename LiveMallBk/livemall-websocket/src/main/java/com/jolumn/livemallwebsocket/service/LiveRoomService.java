package com.jolumn.livemallwebsocket.service;

import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallwebsocket.entity.LiveRoom;
import com.jolumn.livemallwebsocket.manager.WsSessionManager;
import com.jolumn.livemallwebsocket.mapper.LiveRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // 写入关播时的在线人数
        room.setOnlineCount(sessionManager.getRoomOnline(roomId));
        repo.save(room);
        log.info("关播成功: roomId={}, anchorId={}", roomId, anchorId);
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
