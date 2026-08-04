package com.jolumn.vtslshortlinkapi.dto.response;

import com.jolumn.vtslshortlinkapi.entity.Analytics;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AnalyticsItem {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String ipAddress;
    private String os;
    private String device;
    private String browser;
    private String userAgent;
    private String clickedAt;
    private String referrer;
    private String country;

    public static AnalyticsItem from(Analytics a) {
        AnalyticsItem item = new AnalyticsItem();
        item.ipAddress = a.getIpAddress();
        item.os = a.getOs();
        item.device = a.getDevice();
        item.browser = a.getBrowser();
        item.userAgent = a.getUserAgent();
        item.clickedAt = a.getClickedAt().format(FMT);
        item.referrer = a.getReferrer();
        item.country = a.getCountry();
        return item;
    }

    public String getIpAddress() { return ipAddress; }
    public String getOs() { return os; }
    public String getDevice() { return device; }
    public String getBrowser() { return browser; }
    public String getUserAgent() { return userAgent; }
    public String getClickedAt() { return clickedAt; }
    public String getReferrer() { return referrer; }
    public String getCountry() { return country; }
}
