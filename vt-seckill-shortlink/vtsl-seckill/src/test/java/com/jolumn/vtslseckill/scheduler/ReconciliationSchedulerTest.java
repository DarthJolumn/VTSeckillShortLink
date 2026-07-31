package com.jolumn.vtslseckill.scheduler;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import com.jolumn.vtslseckill.entity.SeckillOrder;
import com.jolumn.vtslseckill.entity.enums.SeckillMode;
import com.jolumn.vtslseckill.repository.SeckillActivityRepository;
import com.jolumn.vtslseckill.repository.SeckillOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationSchedulerTest {

    private SeckillOrderRepository orderRepo;
    private SeckillActivityRepository activityRepo;
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private ReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        orderRepo = mock(SeckillOrderRepository.class);
        activityRepo = mock(SeckillActivityRepository.class);
        redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);

        scheduler = new ReconciliationScheduler(orderRepo, activityRepo, redisTemplate);
    }

    @Test
    void shouldSkipDbQueueOrders() {
        SeckillOrder order = mock(SeckillOrder.class);
        when(order.getActivityId()).thenReturn(1L);
        when(order.getUserId()).thenReturn(100L);
        when(order.getOrderNo()).thenReturn("ORD001");

        SeckillActivity activity = mock(SeckillActivity.class);
        when(activity.getMode()).thenReturn(SeckillMode.DB_QUEUE);
        when(activityRepo.findById(1L)).thenReturn(java.util.Optional.of(activity));
        when(orderRepo.findByStatusAndCancelledAtAfter(eq(2), any())).thenReturn(List.of(order));

        scheduler.reconcileCancelledOrders();

        verify(redisTemplate, never()).execute(any(), any());
        verify(activityRepo).findById(1L);
    }

    @Test
    void shouldReconcileRedisOrdersWithOrderedKey() {
        SeckillOrder order = mock(SeckillOrder.class);
        when(order.getActivityId()).thenReturn(1L);
        when(order.getUserId()).thenReturn(100L);
        when(order.getOrderNo()).thenReturn("ORD001");

        SeckillActivity activity = mock(SeckillActivity.class);
        when(activity.getMode()).thenReturn(SeckillMode.REDIS_ASYNC);
        when(activityRepo.findById(1L)).thenReturn(java.util.Optional.of(activity));
        when(orderRepo.findByStatusAndCancelledAtAfter(eq(2), any())).thenReturn(List.of(order));
        when(redisTemplate.hasKey("ordered:{1}:100")).thenReturn(true);

        scheduler.reconcileCancelledOrders();

        verify(redisTemplate, times(1)).hasKey("ordered:{1}:100");
        verify(redisTemplate, times(1)).execute(any(), any());
    }

    @Test
    void shouldSkipOrdersWithoutOrderedKey() {
        SeckillOrder order = mock(SeckillOrder.class);
        when(order.getActivityId()).thenReturn(1L);
        when(order.getUserId()).thenReturn(100L);

        SeckillActivity activity = mock(SeckillActivity.class);
        when(activity.getMode()).thenReturn(SeckillMode.REDIS_ASYNC);
        when(activityRepo.findById(1L)).thenReturn(java.util.Optional.of(activity));
        when(orderRepo.findByStatusAndCancelledAtAfter(eq(2), any())).thenReturn(List.of(order));
        when(redisTemplate.hasKey("ordered:{1}:100")).thenReturn(false);

        scheduler.reconcileCancelledOrders();

        verify(redisTemplate, never()).execute(any(), any());
    }
}