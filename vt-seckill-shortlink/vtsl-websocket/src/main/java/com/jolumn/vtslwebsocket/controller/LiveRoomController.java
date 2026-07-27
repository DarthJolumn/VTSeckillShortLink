package com.jolumn.vtslwebsocket.controller;

import com.jolumn.vtslcommon.annotation.PublicApi;
import com.jolumn.vtslcommon.annotation.RequireAuth;
import com.jolumn.vtslcommon.annotation.RequireRole;
import com.jolumn.vtslcommon.constant.RoleEnum;
import com.jolumn.vtslcommon.context.UserContext;
import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslwebsocket.dto.RoomVO;
import com.jolumn.vtslwebsocket.dto.StartRoomRequest;
import com.jolumn.vtslwebsocket.dto.StopRoomRequest;
import com.jolumn.vtslwebsocket.entity.LiveRoom;
import com.jolumn.vtslwebsocket.service.LiveRoomService;
import com.jolumn.vtslwebsocket.service.UserServiceClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/live")
public class LiveRoomController {

    private final LiveRoomService liveRoomService;
    private final UserServiceClient userServiceClient;

    public LiveRoomController(LiveRoomService liveRoomService,
                              UserServiceClient userServiceClient) {
        this.liveRoomService = liveRoomService;
        this.userServiceClient = userServiceClient;
    }

    /**
     * 开播
     */
    @PostMapping("/room/start")
    @RequireRole({RoleEnum.ANCHOR, RoleEnum.ADMIN})
    public Result<RoomVO> start(@Valid @RequestBody StartRoomRequest request) {
        Long userId = UserContext.currentUserId();
        // 从 user 服务获取主播名称
        String anchorName = userServiceClient.getNickname(userId);
        LiveRoom room = liveRoomService.start(
                userId, anchorName,
                request.getTitle(),
                request.getCategory(),
                request.getCoverColor());
        return Result.ok(RoomVO.from(room));
    }

    /**
     * 关播
     */
    @PostMapping("/room/stop")
    @RequireRole({RoleEnum.ANCHOR, RoleEnum.ADMIN})
    public Result<Void> stop(@Valid @RequestBody StopRoomRequest request) {
        Long userId = UserContext.currentUserId();
        liveRoomService.stop(request.getRoomId(), userId);
        return Result.ok();
    }

    /**
     * 获取当前用户的活跃直播（用于刷新后恢复状态）
     */
    // 不要放在 /room/* 下 — Gateway public-get-paths 把 GET /live/room/* 全放行了
    @GetMapping("/my-active-room")
    @RequireAuth
    public Result<RoomVO> getMyActive() {
        Long userId = UserContext.currentUserId();
        return liveRoomService.findByAnchor(userId)
                .map(room -> Result.ok(RoomVO.from(room)))
                .orElse(Result.ok(null));
    }

    /**
     * 单个直播间信息（公开）
     */
    @GetMapping("/room/{roomId}")
    @PublicApi
    public Result<RoomVO> getRoom(@PathVariable Long roomId) {
        LiveRoom room = liveRoomService.findById(roomId);
        return Result.ok(RoomVO.from(room));
    }

    /**
     * 首页直播间列表（公开）
     */
    @GetMapping("/rooms")
    @PublicApi
    public Result<List<RoomVO>> listRooms() {
        List<LiveRoom> rooms = liveRoomService.listLiveRooms();
        List<RoomVO> vos = rooms.stream()
                .map(RoomVO::from)
                .collect(Collectors.toList());
        return Result.ok(vos);
    }
}
