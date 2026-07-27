package com.jolumn.vtslseckill.repository;

import com.jolumn.vtslseckill.entity.SeckillActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SeckillActivityRepository extends JpaRepository<SeckillActivity, Long> {

    List<SeckillActivity> findByStatusOrderByStartTimeAsc(Integer status);

    List<SeckillActivity> findByRoomIdAndStatusOrderByStartTimeAsc(Long roomId, Integer status);

    /** 管理端：查全部活动（不限状态），按开始时间倒序 */
    List<SeckillActivity> findAllByOrderByStartTimeDesc();

    List<SeckillActivity> findByStatusAndEndTimeBefore(Integer status, java.time.LocalDateTime time);

    /** 查询进行中的活动 ID（布隆过滤器重建用） */
    @Query("SELECT id FROM SeckillActivity WHERE status = 1")
    List<Long> findActiveIds();
}
