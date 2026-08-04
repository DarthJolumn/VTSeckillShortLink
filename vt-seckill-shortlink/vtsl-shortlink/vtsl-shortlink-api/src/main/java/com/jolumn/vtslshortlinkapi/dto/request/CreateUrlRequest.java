package com.jolumn.vtslshortlinkapi.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateUrlRequest {

    @jakarta.validation.constraints.NotBlank(message = "OriginalURL is required")
    private String originalUrl;

    @Size(min = 2, max = 50, message = "ShortKey must be 2-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Invalid ShortKey")
    private String shortKey;

    @Size(max = 255, message = "Title must be at most 255 characters")
    private String title;

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
    public String getShortKey() { return shortKey; }
    public void setShortKey(String shortKey) { this.shortKey = shortKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}
