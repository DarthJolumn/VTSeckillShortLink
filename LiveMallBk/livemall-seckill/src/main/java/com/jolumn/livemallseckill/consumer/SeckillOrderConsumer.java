package com.jolumn.livemallseckill.consumer;

import com.jolumn.livemallcommon.grpc.SeckillPushGrpc;
import com.jolumn.livemallcommon.grpc.SeckillPushOuterClass;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.service.ActivityCacheService;
import com.jolumn.livemallseckill.service.SeckillService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);
    private static final int MAX_RETRIES = 3;
    private final ConcurrentHashMap<String, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final Semaphore SEM;

    private final SeckillService seckillService;
    private final ActivityCacheService cacheService;
    private final SeckillPushGrpc.SeckillPushBlockingStub seckillPushStub;

    public SeckillOrderConsumer(SeckillService seckillService,
                                ActivityCacheService cacheService,
                                SeckillPushGrpc.SeckillPushBlockingStub seckillPushStub,
                                @Value("${seckill.consumer-max-concurrency:30}") int maxConcurrency) {
        this.seckillService = seckillService;
        this.cacheService = cacheService;
        this.seckillPushStub = seckillPushStub;
        this.SEM = new Semaphore(maxConcurrency);
    }

    @KafkaListener(topics = "seckill-order", groupId = "livemall-seckill")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            SEM.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

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
                ack.acknowledge();
            } catch (TransactionException e) {
                String key = record.value();
                AtomicInteger count = retryCounts.computeIfAbsent(key, k -> new AtomicInteger(0));
                int retried = count.incrementAndGet();
                if (retried >= MAX_RETRIES) {
                    log.error("DB/事务异常已达最大重试({}), 放弃并 ack: {}", MAX_RETRIES, key);
                    ack.acknowledge();
                    retryCounts.remove(key);
                } else {
                    log.error("DB/事务异常({}/{}), 不 ack 等待重投: {}", retried, MAX_RETRIES, key);
                }
            } catch (Exception e) {
                log.error("订单消费异常", e);
                ack.acknowledge();
            } finally {
                SEM.release();
            }
        });
    }
}
