package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemalluser.dto.DeviceInfo;
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

//    @GetMapping("/ping")
//    public Result<String> ping() {
//        log.info("pong");
//        return Result.ok("pong");
//    }

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
}
