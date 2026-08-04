package com.jolumn.vtslshortlinkapi.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateUrlRequest {

    @Size(min = 2, max = 50, message = "ShortKey must be 2-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid ShortKey")
    private String shortUrl;

    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    public String getShortUrl() { return shortUrl; }
    public void setShortUrl(String shortUrl) { this.shortUrl = shortUrl; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
