package com.jolumn.livemallwebsocket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StartRoomRequest {

    @NotBlank(message = "直播标题不能为空")
    @Size(max = 80, message = "标题最多 80 字")
    private String title;

    @Size(max = 20)
    private String category;

    private String coverColor;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCoverColor() { return coverColor; }
    public void setCoverColor(String coverColor) { this.coverColor = coverColor; }
}
