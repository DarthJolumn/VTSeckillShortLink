package com.jolumn.livemallcommon.api;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.dto.UserDTO;

/**
 * 用户服务 Dubbo 接口 — 供其他模块跨服务查询用户信息。
 * 例如 WebSocket 弹幕广播需要 nickname / avatar。
 */
public interface UserDubboApi {

    /**
     * 按 ID 查询用户信息
     */
    Result<UserDTO> getById(Long userId);

    /**
     * 按 username 查询
     */
    Result<UserDTO> getByUsername(String username);
}