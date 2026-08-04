package com.jolumn.vtslshortlinkapi.service;

import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslshortlinkapi.dto.response.AnalyticsItem;
import com.jolumn.vtslshortlinkapi.entity.Url;
import com.jolumn.vtslshortlinkapi.repository.AnalyticsRepository;
import com.jolumn.vtslshortlinkapi.repository.UrlRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(UrlRepository urlRepository, AnalyticsRepository analyticsRepository) {
        this.urlRepository = urlRepository;
        this.analyticsRepository = analyticsRepository;
    }

    public List<AnalyticsItem> getAnalytics(Long userId, String urlIdStr) {
        if (urlIdStr == null || urlIdStr.isBlank()) {
            throw new BizException(400, "Missing urlId in path");
        }

        Long urlId;
        try {
            urlId = Long.valueOf(urlIdStr);
        } catch (NumberFormatException e) {
            throw new BizException(404, "No analytics found for this URL");
        }

        Url url = urlRepository.findById(urlId)
                .filter(u -> userId.equals(u.getUserId()))
                .orElseThrow(() -> new BizException(404, "No analytics found for this URL"));

        var list = analyticsRepository.findByUrlIdOrderByClickedAtDesc(urlId);
        if (list.isEmpty()) {
            throw new BizException(404, "No analytics found for this URL");
        }
        return list.stream().map(AnalyticsItem::from).toList();
    }
}
