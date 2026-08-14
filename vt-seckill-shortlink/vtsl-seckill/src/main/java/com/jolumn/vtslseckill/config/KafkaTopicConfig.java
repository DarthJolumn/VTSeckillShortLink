package com.jolumn.vtslseckill.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic 初始化：应用启动时由 KafkaAdmin 自动创建（幂等，已存在则跳过）。
 *
 * <p>为什么用 KafkaAdmin 而非手动脚本：单机秒杀服务启动即保证 topic 存在，
 * 不依赖先跑 init-kafka.sh；partition 数 / 副本数配置化，改配置即可调。</p>
 *
 * <p>partition 设计：6 ≥ 消费并发(concurrency=4)，4 个消费线程瓜分 6 分区全活跃；
 * 单机场景 6 分区足够，未来横向扩展消费者实例时再扩容（partition 只能增不能减，
 * 提前留余量）。顺序性由 key=activityId 保证，与分区数无关。</p>
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topic.seckill-order:seckill-order}")
    private String seckillOrderTopic;

    @Value("${kafka.topic.seckill-order-partitions:6}")
    private int seckillOrderPartitions;

    @Value("${kafka.topic.replication-factor:3}")
    private short replicationFactor;

    /**
     * 秒杀订单 topic：6 partition × RF 3
     * Spring Kafka 自动检测 NewTopic Bean → KafkaAdmin 启动时创建（幂等）
     */
    @Bean
    public NewTopic seckillOrderTopic() {
        return TopicBuilder.name(seckillOrderTopic)
                .partitions(seckillOrderPartitions)
                .replicas(replicationFactor)
                .config("cleanup.policy", "delete")
                .config("retention.ms", "86400000")   // 消息保留 1 天
                .build();
    }
}
