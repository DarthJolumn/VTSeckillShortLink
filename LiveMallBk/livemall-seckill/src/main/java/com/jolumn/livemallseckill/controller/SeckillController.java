package com.jolumn.livemallseckill.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.entity.SeckillOrder;
import com.jolumn.livemallseckill.service.SeckillService;
import jakarta.validation.Valid;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private final SeckillService seckillService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public SeckillController(SeckillService seckillService,
                             KafkaTemplate<String, String> kafkaTemplate) {
        this.seckillService = seckillService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /** 创建秒杀活动（管理员） */
    @PostMapping("/activity")
    public Result<SeckillActivity> createActivity(@Valid @RequestBody SeckillActivity activity) {
        return Result.ok(seckillService.createActivity(activity));
    }

    /** 更新活动状态（上架 1 / 下架 2） */
    @PutMapping("/activity/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        seckillService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    /** 活动详情 */
    @GetMapping("/activity/{id}")
    public Result<SeckillActivity> activityDetail(@PathVariable Long id) {
        return Result.ok(seckillService.getActivity(id));
    }

    /** 活动列表 */
    @GetMapping("/activity/list")
    public Result<List<SeckillActivity>> activityList() {
        return Result.ok(seckillService.getActiveActivities());
    }

    /** 抢购下单 */
    @PostMapping("/order")
    public Result<String> placeOrder(@RequestBody java.util.Map<String, Long> body,
                                     @RequestHeader("X-User-Id") Long userId) {
        Long activityId = body.get("activityId");
        String orderNo = String.valueOf(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);

        String result = seckillService.placeOrder(activityId, userId, orderNo);

        // Lua 扣减成功 → 发 Kafka 异步创建订单
        if ("ok".equals(result)) {
            String msg = userId + ":" + activityId + ":" + orderNo;
            kafkaTemplate.send("seckill-order", msg);
        }
        return Result.ok(result);
    }

    /** 订单列表 */
    @GetMapping("/order/list")
    public Result<List<SeckillOrder>> orderList(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(seckillService.getUserOrders(userId));
    }

    /** 订单详情 */
    @GetMapping("/order/{orderNo}")
    public Result<SeckillOrder> orderDetail(@PathVariable String orderNo) {
        return Result.ok(seckillService.getOrder(orderNo));
    }

    /** 取消订单 */
    @PutMapping("/order/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                    @RequestHeader("X-User-Id") Long userId) {
        seckillService.cancelOrder(orderNo, userId);
        return Result.ok();
    }

    /** 退款 */
    @PutMapping("/order/{orderNo}/refund")
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        seckillService.refundOrder(orderNo);
        return Result.ok();
    }
}
