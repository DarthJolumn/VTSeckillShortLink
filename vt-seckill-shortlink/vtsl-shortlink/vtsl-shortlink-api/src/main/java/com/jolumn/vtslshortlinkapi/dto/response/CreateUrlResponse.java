package com.jolumn.vtslshortlinkapi.dto.response;

import com.jolumn.vtslshortlinkapi.entity.Url;

import java.time.LocalDateTime;

public class CreateUrlResponse {

    private Long id;
    private String originalUrl;
    private String shortUrl;
    private String title;

    public static CreateUrlResponse from(Url url) {
        CreateUrlResponse resp = new CreateUrlResponse();
        resp.id = url.getId();
        resp.originalUrl = url.getOriginalUrl();
        resp.shortUrl = url.getShortKey();
        resp.title = url.getTitle();
        return resp;
    }

    public Long getId() { return id; }
    public String getOriginalUrl() { return originalUrl; }
    public String getShortUrl() { return shortUrl; }
    public String getTitle() { return title; }
}
