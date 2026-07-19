package com.jolumn.livemallseckill.service;

import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallseckill.entity.SeckillActivity;
import com.jolumn.livemallseckill.entity.SeckillOrder;
import com.jolumn.livemallseckill.repository.SeckillActivityRepository;
import com.jolumn.livemallseckill.repository.SeckillOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock private SeckillActivityRepository activityRepo;
    @Mock private SeckillOrderRepository orderRepo;
    @Mock private StockService stockService;

    private SeckillService service;

    @BeforeEach
    void setUp() {
        service = new SeckillService(activityRepo, orderRepo, stockService);
    }

    @Test
    void createActivity_shouldSucceed() {
        SeckillActivity activity = validActivity();
        when(activityRepo.save(any())).thenReturn(activity);

        SeckillActivity result = service.createActivity(activity);
        assertThat(result).isNotNull();
    }

    @Test
    void createActivity_invalidTime_throws() {
        SeckillActivity activity = validActivity();
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        activity.setEndTime(LocalDateTime.now());

        assertThatThrownBy(() -> service.createActivity(activity))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开始时间");
    }

    @Test
    void createActivity_zeroStock_throws() {
        SeckillActivity activity = validActivity();
        activity.setTotalStock(0);

        assertThatThrownBy(() -> service.createActivity(activity))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存");
    }

    @Test
    void placeOrder_success() {
        SeckillActivity activity = validActivity();
        activity.setId(1L);
        when(activityRepo.findById(1L)).thenReturn(Optional.of(activity));
        when(stockService.deduct(1L, 100L)).thenReturn(200);

        String result = service.placeOrder(1L, 100L, "order-001");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void placeOrder_duplicate_throws() {
        SeckillActivity activity = validActivity();
        activity.setId(1L);
        when(activityRepo.findById(1L)).thenReturn(Optional.of(activity));
        when(stockService.deduct(1L, 100L)).thenReturn(-1);

        assertThatThrownBy(() -> service.placeOrder(1L, 100L, "order-001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已参与");
    }

    @Test
    void placeOrder_soldOut_throws() {
        SeckillActivity activity = validActivity();
        activity.setId(1L);
        when(activityRepo.findById(1L)).thenReturn(Optional.of(activity));
        when(stockService.deduct(1L, 100L)).thenReturn(-2);

        assertThatThrownBy(() -> service.placeOrder(1L, 100L, "order-001"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("库存不足");
    }

    @Test
    void createOrder_shouldSave() {
        SeckillActivity activity = validActivity();
        activity.setId(1L);
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SeckillOrder order = service.createOrder(activity, 100L, "order-001");
        assertThat(order.getOrderNo()).isEqualTo("order-001");
        assertThat(order.getStatus()).isEqualTo(0);
    }

    private SeckillActivity validActivity() {
        SeckillActivity a = new SeckillActivity();
        a.setTitle("测试活动");
        a.setProductId(1L);
        a.setSeckillPrice(new BigDecimal("99.00"));
        a.setOriginalPrice(new BigDecimal("199.00"));
        a.setTotalStock(100);
        a.setStartTime(LocalDateTime.now().minusMinutes(5));
        a.setEndTime(LocalDateTime.now().plusHours(1));
        a.setStatus(1);
        return a;
    }
}
