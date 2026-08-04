package com.jolumn.vtslshortlink.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    private static final String STATS_TOPIC = "shortlink-stats";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public StatisticsService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 异步记录点击事件
     * @param shortCode 短码
     */
    public void recordClick(String shortCode) {
        try {
            String message = shortCode + ":" + System.currentTimeMillis();
            kafkaTemplate.send(STATS_TOPIC, shortCode, message);
            log.debug("统计事件发送: shortCode={}", shortCode);
        } catch (Exception e) {
            log.error("统计事件发送失败: shortCode={}", shortCode, e);
        }
    }
}
