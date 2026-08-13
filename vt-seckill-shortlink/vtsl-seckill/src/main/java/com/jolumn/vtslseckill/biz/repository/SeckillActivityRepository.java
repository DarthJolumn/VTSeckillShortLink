package com.jolumn.vtslseckill.biz.repository;

import com.jolumn.vtslseckill.model.entity.SeckillActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeckillActivityRepository extends JpaRepository<SeckillActivity, Long> {

    List<SeckillActivity> findByStatusOrderByStartTimeAsc(Integer status);

    List<SeckillActivity> findByRoomIdAndStatusOrderByStartTimeAsc(Long roomId, Integer status);

    List<SeckillActivity> findAllByOrderByStartTimeDesc();

    List<SeckillActivity> findByStatusAndEndTimeBefore(Integer status, java.time.LocalDateTime time);

    @Query("SELECT id FROM SeckillActivity WHERE status = 1")
    List<Long> findActiveIds();

    @Modifying
    @Query(value = "UPDATE t_seckill_activity SET total_stock = total_stock - 1, version = version + 1 WHERE id = :activityId AND total_stock > 0 AND version = :version",
           nativeQuery = true)
    int decrementStockIfAvailable(@Param("activityId") Long activityId, @Param("version") int version);

    @Modifying
    @Query(value = "UPDATE t_seckill_activity SET total_stock = total_stock + 1, version = version + 1 WHERE id = :activityId",
           nativeQuery = true)
    void incrementStockIfAvailable(@Param("activityId") Long activityId);
}
