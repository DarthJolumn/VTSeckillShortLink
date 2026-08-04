package com.jolumn.vtslshortlinkapi.service;

import org.springframework.stereotype.Service;

@Service
public class UserAgentParser {

    public record UaInfo(String device, String browser, String os) {}

    public UaInfo parse(String ua) {
        if (ua == null || ua.isBlank()) return new UaInfo("Desktop", "", "");

        String device = detectDevice(ua);
        String browser = detectBrowser(ua);
        String os = detectOs(ua);

        return new UaInfo(device, browser, os);
    }

    private String detectDevice(String ua) {
        String lower = ua.toLowerCase();
        if (lower.contains("bot") || lower.contains("crawler") || lower.contains("spider") || lower.contains("slackbot")) {
            return "Bot";
        }
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone") || lower.contains("ipod")) {
            return "Mobile";
        }
        return "Desktop";
    }

    private String detectBrowser(String ua) {
        if (ua.contains("Edg/")) return extractVersion(ua, "Edg/");
        if (ua.contains("Chrome/")) return extractVersion(ua, "Chrome/");
        if (ua.contains("Firefox/")) return extractVersion(ua, "Firefox/");
        if (ua.contains("Safari/") && !ua.contains("Chrome")) return extractVersion(ua, "Version/");
        return "";
    }

    private String detectOs(String ua) {
        if (ua.contains("Windows NT 10")) return "Windows";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) return "iOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Mac OS X")) return "Mac OS X";
        if (ua.contains("Linux")) return "Linux";
        return "";
    }

    private String extractVersion(String ua, String prefix) {
        int idx = ua.indexOf(prefix);
        if (idx < 0) return "";
        String name = prefix.replace("/", "");
        int start = idx + prefix.length();
        int end = start;
        while (end < ua.length() && (Character.isDigit(ua.charAt(end)) || ua.charAt(end) == '.')) {
            end++;
        }
        if (end == start) return name;
        return name + " " + ua.substring(start, end);
    }
}
