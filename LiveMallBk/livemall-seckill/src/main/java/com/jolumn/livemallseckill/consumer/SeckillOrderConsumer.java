package com.jolumn.livemallseckill.consumer;

import com.jolumn.livemallcommon.grpc.SeckillPushGrpc;
import com.jolumn.livemallcommon.grpc.SeckillPushOuterClass;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.service.ActivityCacheService;
import com.jolumn.livemallseckill.service.SeckillService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final SeckillService seckillService;
    private final ActivityCacheService cacheService;
    private final SeckillPushGrpc.SeckillPushBlockingStub seckillPushStub;

    public SeckillOrderConsumer(SeckillService seckillService,
                                ActivityCacheService cacheService,
                                SeckillPushGrpc.SeckillPushBlockingStub seckillPushStub) {
        this.seckillService = seckillService;
        this.cacheService = cacheService;
        this.seckillPushStub = seckillPushStub;
    }

    @KafkaListener(topics = "seckill-order", groupId = "livemall-seckill")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Thread.startVirtualThread(() -> {
            try {
                String[] parts = record.value().split(":", 3);
                if (parts.length < 3) {
                    log.error("Kafka 消息格式错误: {}", record.value());
                    ack.acknowledge();
                    return;
                }
                Long userId = Long.parseLong(parts[0]);
                Long activityId = Long.parseLong(parts[1]);
                String orderNo = parts[2];

                SeckillActivity activity = cacheService.getActivity(activityId);
                if (activity == null) {
                    log.error("活动不存在: activityId={}", activityId);
                    ack.acknowledge();
                    return;
                }

                seckillService.createOrder(activity, userId, orderNo);

                // gRPC 推送秒杀结果到 WebSocket 服务
                try {
                    SeckillPushOuterClass.SeckillPushResponse resp = seckillPushStub.pushResult(
                            SeckillPushOuterClass.SeckillPushRequest.newBuilder()
                                    .setUserId(userId)
                                    .setOrderNo(orderNo)
                                    .setSuccess(true)
                                    .setMessage("抢购成功")
                                    .setTimestamp(System.currentTimeMillis())
                                    .build());
                    if (!resp.getDelivered()) {
                        log.warn("gRPC 推送失败: userId={}, reason={}", userId, resp.getReason());
                    }
                } catch (Exception e) {
                    log.warn("gRPC 推送异常, 降级: userId={}, error={}", userId, e.getMessage());
                }

                ack.acknowledge();
            } catch (DuplicateKeyException e) {
                log.warn("重复订单（幂等兜底）: orderNo={}", e.getMessage());
                ack.acknowledge(); // 不可重试，直接 ack 跳过
            } catch (DataAccessException e) {
                log.error("DB 不可用, 暂停消费等待重试: {}", e.getMessage());
                // 不 ack → Kafka 重试（配合 seek 回到当前 offset）
            } catch (Exception e) {
                log.error("订单消费异常", e);
                ack.acknowledge();
            }
        });
    }
}
