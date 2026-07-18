package com.jolumn.livemallwebsocket.dto;

import jakarta.validation.constraints.NotNull;

public class StopRoomRequest {

    @NotNull(message = "roomId 不能为空")
    private Long roomId;

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
}
