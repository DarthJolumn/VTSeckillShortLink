package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.annotation.RequireAuth;
import com.jolumn.livemallcommon.context.UserContext;
import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemalluser.dto.DeviceInfo;
import com.jolumn.livemalluser.dto.UpdatePasswordRequest;
import com.jolumn.livemalluser.dto.UpdateProfileRequest;
import com.jolumn.livemalluser.dto.UserProfileVO;
import com.jolumn.livemalluser.entity.User;
import com.jolumn.livemalluser.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserService userService;

    @GetMapping("/profile")
    @RequireAuth
    public Result<UserProfileVO> getProfile() {
        Long userId = UserContext.currentUserId();
        User user = userService.findById(userId);
        UserProfileVO vo = toProfileVO(user);
        return Result.ok(vo);
    }

    @GetMapping("/balance")
    @RequireAuth
    public Result<java.math.BigDecimal> getBalance() {
        Long userId = UserContext.currentUserId();
        return Result.ok(userService.getBalance(userId));
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
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        Long userId = UserContext.currentUserId();
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
    public Result<Void> kickDevice(@PathVariable String deviceId) {
        Long userId = UserContext.currentUserId();
        userService.kickDevice(userId, deviceId);
        return Result.ok();
    }

    @PutMapping("/profile")
    @RequireAuth
    public Result<UserProfileVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = UserContext.currentUserId();
        userService.updateProfile(userId, request.getNickname(), request.getAvatar(), request.getPhone());
        User user = userService.findById(userId);
        return Result.ok(toProfileVO(user));
    }

    @PutMapping("/password")
    @RequireAuth
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        Long userId = UserContext.currentUserId();
        userService.updatePassword(userId, request.getOldPassword(), request.getNewPassword());
        return Result.ok();
    }

    @PutMapping("/ban/{userId}")
    @RequireAuth
    public Result<Void> banUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Integer> body) {
        userService.updateBanStatus(userId, body.get("status"));
        return Result.ok();
    }
}
