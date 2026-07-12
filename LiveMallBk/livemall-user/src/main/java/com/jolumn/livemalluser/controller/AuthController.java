package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.util.IdempotencyService;
import com.jolumn.livemalluser.dto.RegisterRequest;
import com.jolumn.livemalluser.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final IdempotencyService idempotencyService;


    // TODO 第三方注册
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
}
