package com.jolumn.livemallwebsocket.dto;

import java.time.LocalDateTime;

public class RoomVO {

    private Long id;
    private String title;
    private String anchorName;
    private String category;
    private String coverColor;
    private Integer onlineCount;
    private Integer status;
    private LocalDateTime startedAt;

    public static RoomVO from(com.jolumn.livemallwebsocket.entity.LiveRoom room) {
        RoomVO vo = new RoomVO();
        vo.id = room.getId();
        vo.title = room.getTitle();
        vo.anchorName = room.getAnchorName();
        vo.category = room.getCategory();
        vo.coverColor = room.getCoverColor();
        vo.onlineCount = room.getOnlineCount();
        vo.status = room.getStatus();
        vo.startedAt = room.getStartedAt();
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAnchorName() { return anchorName; }
    public void setAnchorName(String anchorName) { this.anchorName = anchorName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCoverColor() { return coverColor; }
    public void setCoverColor(String coverColor) { this.coverColor = coverColor; }

    public Integer getOnlineCount() { return onlineCount; }
    public void setOnlineCount(Integer onlineCount) { this.onlineCount = onlineCount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
}
