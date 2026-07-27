package com.jolumn.vtslwebsocket.service;

import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.dto.UserDTO;
import com.jolumn.vtslcommon.api.UserDubboApi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 通过 Dubbo RPC 调用 user 服务获取用户信息。
 * 因为开播时需要主播的 nickname 作为展示名。
 */
@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    @DubboReference(check = false)
    private UserDubboApi userDubboApi;

    public String getNickname(Long userId) {
        try {
            Result<UserDTO> result = userDubboApi.getById(userId);
            if (result != null && result.isSuccess() && result.getData() != null) {
                UserDTO user = result.getData();
                return user.getNickname() != null ? user.getNickname() : user.getUsername();
            }
        } catch (Exception e) {
            log.warn("Dubbo 调用 user 服务失败，使用 userId 作为 fallback: {}", e.getMessage());
        }
        return "user_" + userId;
    }
}
