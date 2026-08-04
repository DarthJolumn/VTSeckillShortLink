package com.jolumn.vtslshortlink.consumer;

import com.jolumn.vtslshortlink.repository.LinkClickStatsRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class StatisticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatisticsConsumer.class);

    private final LinkClickStatsRepository statsRepository;

    public StatisticsConsumer(LinkClickStatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    @KafkaListener(topics = "shortlink-stats", groupId = "vtsl-shortlink-stats")
    @Transactional
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            String shortCode = record.key();
            LocalDate clickDate = LocalDate.now();

            statsRepository.incrementClickCount(shortCode, clickDate);
            log.debug("点击统计写入 MySQL: shortCode={}, date={}", shortCode, clickDate);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("点击统计写入失败: {}", record.value(), e);
            ack.acknowledge();
        }
    }
}
