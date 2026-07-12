package com.jolumn.livemalluser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallcommon.util.IdempotencyService;
import com.jolumn.livemallcommon.util.JwtUtil;
import com.jolumn.livemalluser.dto.LoginRequest;
import com.jolumn.livemalluser.dto.LoginResponse;
import com.jolumn.livemalluser.dto.RegisterRequest;
import com.jolumn.livemalluser.entity.User;
import com.jolumn.livemalluser.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final IdempotencyService idempotencyService;


    public String preRegister(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    @Transactional
    public void register(RegisterRequest request, String hashedPwd) {
        String username = request.getUsername().trim();
        if (existsByUsername(username)) {
            throw new BizException(1012, "用户名已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(hashedPwd);
        user.setPhone(request.getPhone());
        user.setRole(1);
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            log.warn("并发注册冲突, username={}", username, e);
            throw new BizException(1012, "用户名已被注册");
        }
    }

    public LoginResponse login(LoginRequest request, String deviceId, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            LoginResponse cached = idempotencyService.get(idempotencyKey, LoginResponse.class);
            if (cached != null) {
                return cached;
            }
        }

        String username = request.getUsername().trim();
        User user = findByUsername(username);
        if (user == null) {
            log.warn("登录失败 · 用户不存在, username={}, inputPwdLen={}", username, request.getPassword().length());
            throw new BizException(1011, "用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            log.warn("登录失败 · 账号已封禁, username={}", username);
            throw new BizException(1006, "账号已被封禁");
        }

        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            log.warn("登录失败 · 密码不匹配, username={}, inputPwdLen={}, dbPwdPrefix={}",
                    username, request.getPassword().length(), user.getPassword().substring(0, 25));
            throw new BizException(1011, "用户名或密码错误");
        }
        log.info("登录成功, userId={}, username={}", user.getId(), username);

        String accessToken = jwtUtil.generate(user.getId(), user.getRole(), 900);
        String refreshToken = "rft_" + UUID.randomUUID().toString().replace("-", "");
        String refreshKey = "refresh:" + refreshToken;
        String deviceSessionKey = "device_sessions:" + user.getId();

        try {
            redisTemplate.opsForValue().set(refreshKey,
                    user.getId() + ":" + user.getRole() + ":" + deviceId, 7, TimeUnit.DAYS);
            redisTemplate.opsForSet().add(deviceSessionKey, deviceId);
            redisTemplate.expire(deviceSessionKey, 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("登录时 Redis 写入失败, userId={}", user.getId(), e);
            throw new BizException(500, "系统繁忙，请稍后重试");
        }

        LoginResponse response = LoginResponse.of(accessToken, refreshToken);
        if (idempotencyKey != null) {
            idempotencyService.set(idempotencyKey, response, 5, TimeUnit.MINUTES);
        }
        return response;
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }
}
