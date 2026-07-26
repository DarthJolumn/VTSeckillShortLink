package com.jolumn.livemallshortlink.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class StatisticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatisticsConsumer.class);

    @KafkaListener(topics = "shortlink-stats", groupId = "livemall-shortlink-stats")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String shortCode = record.key();
            String timestamp = record.value();
            
            // TODO: 批量聚合写入 ClickHouse
            // 当前仅打印日志，后续接入 ClickHouse
            log.info("统计事件消费: shortCode={}, timestamp={}", shortCode, timestamp);
            
            ack.acknowledge();
        } catch (Exception e) {
            log.error("统计事件消费失败: {}", record.value(), e);
            ack.acknowledge(); // 异常也 ack，避免重复消费
        }
    }
}
