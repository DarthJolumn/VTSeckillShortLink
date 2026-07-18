package com.jolumn.livemallwebsocket.mapper;

import com.jolumn.livemallwebsocket.entity.LiveRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveRoomRepository extends JpaRepository<LiveRoom, Long> {

    /** 查该主播当前是否有进行中的直播（幂等：重复开播返回已有房间） */
    Optional<LiveRoom> findByAnchorIdAndStatus(Long anchorId, Integer status);

    /** 查询所有直播中的房间，按开播时间倒序 */
    List<LiveRoom> findByStatusOrderByStartedAtDesc(Integer status);
}
