package com.jolumn.livemallseckill.repository;

import com.jolumn.livemallseckill.entity.SeckillActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeckillActivityRepository extends JpaRepository<SeckillActivity, Long> {

    List<SeckillActivity> findByStatusOrderByStartTimeAsc(Integer status);

    List<SeckillActivity> findByRoomIdAndStatusOrderByStartTimeAsc(Long roomId, Integer status);

    /** 管理端：查全部活动（不限状态），按开始时间倒序 */
    List<SeckillActivity> findAllByOrderByStartTimeDesc();

    List<SeckillActivity> findByStatusAndEndTimeBefore(Integer status, java.time.LocalDateTime time);
}
