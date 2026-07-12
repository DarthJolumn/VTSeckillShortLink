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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private UserService userService;

    private static String REAL_HASH;

    @BeforeAll
    static void setUp() {
        REAL_HASH = BCrypt.hashpw("Test1234", BCrypt.gensalt(10));
    }

    @Test
    void preRegister_returnsBcryptHash() {
        String hash = userService.preRegister("Test1234");
        assertThat(hash).startsWith("$2a$10$");
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Test1234");
        String hashedPwd = "$2a$10$xxxx";

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doReturn(1).when(userMapper).insert((User) any());

        assertThatCode(() -> userService.register(request, hashedPwd))
                .doesNotThrowAnyException();
        verify(userMapper).insert((User) any());
    }

    @Test
    void register_usernameExists_throws1012() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        String hashedPwd = "$2a$10$xxxx";

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> userService.register(request, hashedPwd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1012);
        verify(userMapper, never()).insert((User) any());
    }

    @Test
    void register_concurrentInsert_throws1012() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        String hashedPwd = "$2a$10$xxxx";

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doThrow(new DuplicateKeyException("dup")).when(userMapper).insert((User) any());

        assertThatThrownBy(() -> userService.register(request, hashedPwd))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1012);
    }

    @Test
    void register_usernameTrimmed() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("  testuser  ");
        String hashedPwd = "$2a$10$xxxx";

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doReturn(1).when(userMapper).insert((User) any());

        userService.register(request, hashedPwd);
        verify(userMapper).insert((User) any());
    }

    @Test
    void login_success() {
        LoginRequest request = loginReq("zhangsan", "Test1234");
        User user = buildUser(1L, "zhangsan", REAL_HASH, 1);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);

        when(redisTemplate.opsForValue()).thenReturn(valOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(jwtUtil.generate(1L, 1, 900)).thenReturn("jwt.xxx");

        LoginResponse resp = userService.login(request, "device-uuid", null);

        assertThat(resp.getAccessToken()).isEqualTo("jwt.xxx");
        assertThat(resp.getRefreshToken()).startsWith("rft_");
        assertThat(resp.getExpiresIn()).isEqualTo(900);
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        verify(valOps).set(anyString(), anyString(), eq(7L), eq(TimeUnit.DAYS));
        verify(setOps).add(anyString(), eq("device-uuid"));
    }

    @Test
    void login_idempotentReturnsCached() {
        LoginRequest request = loginReq("zhangsan", "Test1234");
        LoginResponse cached = LoginResponse.of("cached-access", "cached-refresh");

        when(idempotencyService.get("key-123", LoginResponse.class)).thenReturn(cached);

        LoginResponse resp = userService.login(request, "device-uuid", "key-123");

        assertThat(resp.getAccessToken()).isEqualTo("cached-access");
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void login_userNotFound_throws1011() {
        LoginRequest request = loginReq("nobody", "Test1234");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> userService.login(request, "uuid", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1011);
    }

    @Test
    void login_wrongPassword_throws1011() {
        LoginRequest request = loginReq("zhangsan", "WrongPassword");
        User user = buildUser(1L, "zhangsan", REAL_HASH, 1);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> userService.login(request, "uuid", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1011);
    }

    @Test
    void login_bannedUser_throws1006() {
        LoginRequest request = loginReq("banned", "Test1234");
        User user = buildUser(1L, "banned", REAL_HASH, 0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> userService.login(request, "uuid", null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(1006);
    }

    private LoginRequest loginReq(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    private User buildUser(Long id, String username, String password, int status) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(password);
        u.setRole(1);
        u.setStatus(status);
        return u;
    }
}
