package com.jolumn.livemalluser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jolumn.livemallcommon.api.WsPushService;
import com.jolumn.livemallcommon.exception.BizException;
import com.jolumn.livemallcommon.util.IdempotencyService;
import com.jolumn.livemallcommon.util.JwtUtil;
import com.jolumn.livemalluser.dto.DeviceInfo;
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

import org.apache.dubbo.config.annotation.DubboReference;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final IdempotencyService idempotencyService;

    @DubboReference
    private WsPushService wsPushService;

    private final ConcurrentHashMap<String, CompletableFuture<LoginResponse>>
            pendingRefreshes = new ConcurrentHashMap<>();


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

        String accessToken = jwtUtil.generate(user.getId(), user.getRole(), 86400);
        String refreshToken = "rft_" + UUID.randomUUID().toString().replace("-", "");
        String refreshKey = "refresh:" + refreshToken;
        String activeKey = "active_token:" + user.getId() + ":" + deviceId;

        try {
            String oldRefreshKey = redisTemplate.opsForValue().get(activeKey);
            if (oldRefreshKey != null) {
                redisTemplate.delete(oldRefreshKey);
            }
            redisTemplate.opsForValue().set(refreshKey,
                    user.getId() + ":" + user.getRole() + ":" + deviceId, 7, TimeUnit.DAYS);
            redisTemplate.opsForValue().set(activeKey, refreshKey, 7, TimeUnit.DAYS);
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

    public void logout(String refreshToken) {
        String refreshKey = "refresh:" + refreshToken;
        String value = redisTemplate.opsForValue().get(refreshKey);
        if (value == null) {
            return;
        }
        redisTemplate.delete(refreshKey);
        String[] parts = value.split(":", 3);
        if (parts.length < 3) {
            log.error("退出登录时 token value 格式异常, refreshToken={}, value={}", refreshToken, value);
            return;
        }
        String userId = parts[0];
        String deviceId = parts[2];
        redisTemplate.delete("active_token:" + userId + ":" + deviceId);
    }

    public LoginResponse refresh(String refreshToken) {
        // ── 请求合并：同一个 token 的并发请求只执行一次 ──
        CompletableFuture<LoginResponse> existing = pendingRefreshes.get(refreshToken);
        if (existing != null) {
            try {
                return existing.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("等待刷新超时, refreshToken={}", maskToken(refreshToken));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(500, "系统繁忙");
            } catch (ExecutionException e) {
                throw new BizException(1013, "登录已过期，请重新登录");
            }
        }

        CompletableFuture<LoginResponse> future = new CompletableFuture<>();
        CompletableFuture<LoginResponse> old = pendingRefreshes.putIfAbsent(refreshToken, future);
        if (old != null) {
            try {
                return old.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new BizException(1013, "登录已过期，请重新登录");
            }
        }

        try {
            // ── 分布式锁：防止同一 token 被两个节点同时刷新 ──
            String lockKey = "refresh:lock:" + refreshToken;
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", 3, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                throw new BizException(1013, "登录已过期，请重新登录");
            }

            // ── Rotation：GET 旧 token → DELETE → 签发新 token ──
            String refreshKey = "refresh:" + refreshToken;
            String value = redisTemplate.opsForValue().get(refreshKey);
            if (value == null) {
                throw new BizException(1013, "登录已过期，请重新登录");
            }
            redisTemplate.delete(refreshKey);

            String[] parts = value.split(":", 3);
            if (parts.length < 3) {
                log.error("刷新时 token value 格式异常, refreshToken={}, value={}", refreshToken, value);
                throw new BizException(500, "token 数据异常");
            }

            Long userId = Long.valueOf(parts[0]);
            Integer role = Integer.valueOf(parts[1]);
            String deviceId = parts[2];

            // 签发新 Access Token
            String newAccessToken = jwtUtil.generate(userId, role, 86400);

            // 新 Refresh Token（Rotation）
            String newRefreshToken = "rft_" + UUID.randomUUID().toString().replace("-", "");
            String newRefreshKey = "refresh:" + newRefreshToken;
            redisTemplate.opsForValue().set(
                    newRefreshKey,
                    userId + ":" + role + ":" + deviceId,
                    7, TimeUnit.DAYS);
            redisTemplate.opsForValue().set(
                    "active_token:" + userId + ":" + deviceId, newRefreshKey, 7, TimeUnit.DAYS);

            LoginResponse resp = LoginResponse.of(newAccessToken, newRefreshToken);
            future.complete(resp);
            return resp;

        } catch (BizException e) {
            future.completeExceptionally(e);
            throw e;
        } finally {
            pendingRefreshes.remove(refreshToken);
            redisTemplate.delete("refresh:lock:" + refreshToken);
        }
    }

    private String maskToken(String token) {
        return token != null && token.length() > 10
                ? token.substring(0, 10) + "***" : "***";
    }

    public List<DeviceInfo> getDevices(Long userId) {
        String prefix = "active_token:" + userId + ":";
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(k -> k.substring(prefix.length()))
                .map(did -> DeviceInfo.of(did, false))
                .collect(Collectors.toList());
    }

    public void kickDevice(Long userId, String deviceId) {
        String activeKey = "active_token:" + userId + ":" + deviceId;
        String refreshKey = redisTemplate.opsForValue().get(activeKey);
        if (refreshKey == null) {
            throw new BizException(404, "设备不在线");
        }

        redisTemplate.delete(refreshKey);
        redisTemplate.delete(activeKey);

        try {
            wsPushService.kickDevice(userId, deviceId);
        } catch (Exception e) {
            log.warn("踢设备推送失败(已清除Redis): userId={}, deviceId={}", userId, deviceId, e);
        }
    }

    public User findById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /** 更新个人资料（昵称/头像/手机号） */
    @Transactional
    public void updateProfile(Long userId, String nickname, String avatar, String phone) {
        User user = findById(userId);
        if (nickname != null && !nickname.isBlank()) {
            user.setNickname(nickname.trim());
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (phone != null && !phone.isBlank()) {
            // 校验手机号唯一性
            User exist = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
            if (exist != null && !exist.getId().equals(userId)) {
                throw new BizException(1014, "手机号已被使用");
            }
            user.setPhone(phone.trim());
        }
        userMapper.updateById(user);
    }

    /** 修改密码 */
    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = findById(userId);
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BizException(1010, "旧密码错误");
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt(10)));
        userMapper.updateById(user);
    }

    /** 封禁/解封用户 */
    @Transactional
    public void updateBanStatus(Long userId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BizException(400, "状态值必须为 0（封禁）或 1（正常）");
        }
        User user = findById(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /** 查询余额 */
    public java.math.BigDecimal getBalance(Long userId) {
        return findById(userId).getBalance();
    }

    /** 扣款（送礼时调用） */
    @Transactional
    public void deductBalance(Long userId, java.math.BigDecimal amount) {
        User user = findById(userId);
        if (user.getBalance() == null || user.getBalance().compareTo(amount) < 0) {
            throw new BizException(1015, "余额不足");
        }
        user.setBalance(user.getBalance().subtract(amount));
        userMapper.updateById(user);
    }
}
