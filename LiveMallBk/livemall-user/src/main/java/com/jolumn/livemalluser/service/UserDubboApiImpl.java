package com.jolumn.livemalluser.service;

import com.jolumn.livemallcommon.api.UserDubboApi;
import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.dto.UserDTO;
import com.jolumn.livemalluser.entity.User;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
public class UserDubboApiImpl implements UserDubboApi {

    private final UserService userService;

    public UserDubboApiImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Result<UserDTO> getById(Long userId) {
        try {
            User user = userService.findById(userId);
            UserDTO dto = toDTO(user);
            return Result.ok(dto);
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    @Override
    public Result<UserDTO> getByUsername(String username) {
        User user = userService.findByUsername(username);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        UserDTO dto = toDTO(user);
        return Result.ok(dto);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        return dto;
    }
}