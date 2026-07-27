package com.jolumn.vtsluser.controller;

import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.util.IdempotencyService;
import com.jolumn.vtslcommon.util.JwtUtil;
import com.jolumn.vtsluser.dto.LoginRequest;
import com.jolumn.vtsluser.dto.LoginResponse;
import com.jolumn.vtsluser.dto.LogoutRequest;
import com.jolumn.vtsluser.dto.RefreshRequest;
import com.jolumn.vtsluser.dto.RegisterRequest;
import com.jolumn.vtsluser.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final IdempotencyService idempotencyService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public Result<Void> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        if (idempotencyKey != null
                && !idempotencyKey.isBlank()
                && !idempotencyService.tryAcquire(idempotencyKey)) {
            return Result.ok();
        }
        String hashedPwd = userService.preRegister(request.getPassword());
        userService.register(request, hashedPwd);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        LoginResponse response = userService.login(request, deviceId, idempotencyKey);
        return Result.ok(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutRequest request) {
        userService.logout(request.getRefreshToken());
        return Result.ok();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResponse response = userService.refresh(request.getRefreshToken());
        return Result.ok(response);
    }
}
