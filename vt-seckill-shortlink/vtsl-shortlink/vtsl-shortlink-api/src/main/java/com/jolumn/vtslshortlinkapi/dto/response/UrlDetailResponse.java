package com.jolumn.vtslshortlinkapi.dto.response;

import com.jolumn.vtslshortlinkapi.entity.Url;

import java.time.LocalDateTime;

public class UrlDetailResponse {

    private Long id;
    private String originalUrl;
    private String shortUrl;
    private String title;
    private Integer clicks;
    private LocalDateTime createdAt;

    public static UrlDetailResponse from(Url url) {
        UrlDetailResponse resp = new UrlDetailResponse();
        resp.id = url.getId();
        resp.originalUrl = url.getOriginalUrl();
        resp.shortUrl = url.getShortKey();
        resp.title = url.getTitle();
        resp.clicks = url.getClicks();
        resp.createdAt = url.getCreatedAt();
        return resp;
    }

    public Long getId() { return id; }
    public String getOriginalUrl() { return originalUrl; }
    public String getShortUrl() { return shortUrl; }
    public String getTitle() { return title; }
    public Integer getClicks() { return clicks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
