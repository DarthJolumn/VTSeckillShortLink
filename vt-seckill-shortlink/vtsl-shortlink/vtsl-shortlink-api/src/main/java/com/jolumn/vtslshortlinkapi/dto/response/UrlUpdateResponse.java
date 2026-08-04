package com.jolumn.vtslshortlinkapi.dto.response;

import com.jolumn.vtslshortlinkapi.entity.Url;

import java.time.LocalDateTime;

public class UrlUpdateResponse {

    private Long id;
    private String originalUrl;
    private String shortUrl;
    private String title;
    private Integer clicks;
    private LocalDateTime updatedAt;

    public static UrlUpdateResponse from(Url url) {
        UrlUpdateResponse resp = new UrlUpdateResponse();
        resp.id = url.getId();
        resp.originalUrl = url.getOriginalUrl();
        resp.shortUrl = url.getShortKey();
        resp.title = url.getTitle();
        resp.clicks = url.getClicks();
        resp.updatedAt = url.getUpdatedAt();
        return resp;
    }

    public Long getId() { return id; }
    public String getOriginalUrl() { return originalUrl; }
    public String getShortUrl() { return shortUrl; }
    public String getTitle() { return title; }
    public Integer getClicks() { return clicks; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
