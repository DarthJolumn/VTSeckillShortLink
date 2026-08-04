package com.jolumn.vtslshortlinkapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
public class GeoIpService {

    private static final Logger log = LoggerFactory.getLogger(GeoIpService.class);

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final Duration cacheTtl;

    public GeoIpService(StringRedisTemplate redisTemplate,
                        @Value("${shortly.geoip.base-url:https://ipapi.co}") String baseUrl,
                        @Value("${shortly.geoip.timeout-seconds:2}") int timeoutSeconds,
                        @Value("${shortly.geoip.cache-ttl-hours:24}") int cacheTtlHours) {
        this.redisTemplate = redisTemplate;
        this.cacheTtl = Duration.ofHours(cacheTtlHours);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String countryOf(String ip) {
        if (ip == null || ip.isBlank()) return "Unknown";

        String cacheKey = "ip-country:" + ip;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        if ("127.0.0.1".equals(ip) || "::1".equals(ip)) return "Localhost";

        try {
            String json = restClient.get()
                    .uri("/{ip}/json/", ip)
                    .retrieve()
                    .body(String.class);
            String country = extractCountry(json);
            if (country != null && !country.isBlank()) {
                redisTemplate.opsForValue().set(cacheKey, country, cacheTtl);
                return country;
            }
        } catch (Exception e) {
            log.warn("ipapi.co failed for {}", ip, e);
        }
        return "Unknown";
    }

    private String extractCountry(String json) {
        if (json == null) return null;
        int idx = json.indexOf("\"country_name\"");
        if (idx < 0) return null;
        int start = json.indexOf("\"", idx + 14) + 1;
        int end = json.indexOf("\"", start);
        if (start <= 0 || end <= start) return null;
        return json.substring(start, end);
    }
}
