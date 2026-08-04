package com.jolumn.vtslshortlinkapi.service;

import com.jolumn.vtslshortlinkapi.entity.Analytics;
import com.jolumn.vtslshortlinkapi.repository.AnalyticsRepository;
import com.jolumn.vtslshortlinkapi.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AnalyticsAsyncService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAsyncService.class);

    private final UrlRepository urlRepository;
    private final AnalyticsRepository analyticsRepository;
    private final GeoIpService geoIpService;
    private final UserAgentParser userAgentParser;

    public AnalyticsAsyncService(UrlRepository urlRepository,
                                 AnalyticsRepository analyticsRepository,
                                 GeoIpService geoIpService,
                                 UserAgentParser userAgentParser) {
        this.urlRepository = urlRepository;
        this.analyticsRepository = analyticsRepository;
        this.geoIpService = geoIpService;
        this.userAgentParser = userAgentParser;
    }

    @Async("shortlyAsyncExecutor")
    public void record(Long urlId, String ip, String userAgent, String referer) {
        try {
            urlRepository.incrementClicks(urlId);

            String country = geoIpService.countryOf(ip);
            var uaInfo = userAgentParser.parse(userAgent);

            Analytics analytics = new Analytics();
            analytics.setUrlId(urlId);
            analytics.setClickedAt(LocalDateTime.now());
            analytics.setIpAddress(ip);
            analytics.setUserAgent(userAgent != null ? userAgent : "");
            analytics.setReferrer(referer);
            analytics.setCountry(country);
            analytics.setDevice(uaInfo.device());
            analytics.setBrowser(uaInfo.browser());
            analytics.setOs(uaInfo.os());

            analyticsRepository.save(analytics);
        } catch (Exception e) {
            log.error("Failed to record analytics for urlId={}", urlId, e);
        }
    }
}
