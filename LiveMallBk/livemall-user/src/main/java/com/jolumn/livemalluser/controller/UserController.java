package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.annotation.RequireAuth;
import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemalluser.dto.DeviceInfo;
import com.jolumn.livemalluser.dto.UserProfileVO;
import com.jolumn.livemalluser.entity.User;
import com.jolumn.livemalluser.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserService userService;

    @GetMapping("/profile")
    @RequireAuth
    public Result<UserProfileVO> getProfile(@RequestHeader("X-User-Id") Long userId) {
        User user = userService.findById(userId);
        UserProfileVO vo = toProfileVO(user);
        return Result.ok(vo);
    }

//    @GetMapping("/ping")
//    public Result<String> ping() {
//        log.info("pong");
//        return Result.ok("pong");
//    }

    private UserProfileVO toProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }

    @GetMapping("/devices")
    public Result<List<DeviceInfo>> getDevices(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        List<DeviceInfo> devices = userService.getDevices(userId);
        if (deviceId != null) {
            devices.stream()
                    .filter(d -> d.getDeviceId().equals(deviceId))
                    .findFirst()
                    .ifPresent(d -> d.setCurrent(true));
        }
        return Result.ok(devices);
    }

    @DeleteMapping("/devices/{deviceId}")
    @RequireAuth
    public Result<Void> kickDevice(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String deviceId) {
        userService.kickDevice(userId, deviceId);
        return Result.ok();
    }
}
