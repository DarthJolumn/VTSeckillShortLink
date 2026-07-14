package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.exception.GlobalExceptionHandler;
import com.jolumn.livemallcommon.util.IdempotencyService;
import com.jolumn.livemallcommon.util.JwtUtil;
import com.jolumn.livemalluser.dto.LoginResponse;
import com.jolumn.livemalluser.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private IdempotencyService idempotencyService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void register_ok() throws Exception {
        when(idempotencyService.tryAcquire(anyString())).thenReturn(true);
        when(userService.preRegister(anyString())).thenReturn("$2a$10$xxxx");
        doNothing().when(userService).register(any(), anyString());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"testuser","password":"Test1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE));
    }

    @Test
    void register_idempotent_returnsOk() throws Exception {
        when(idempotencyService.tryAcquire("key-123")).thenReturn(false);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Idempotency-Key", "key-123")
                        .content("""
                                {"username":"testuser","password":"Test1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE));

        verify(userService, never()).preRegister(anyString());
        verify(userService, never()).register(any(), anyString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"username":"ab","password":"Test1234"}             | 用户名长度须在 4~50 之间
            {"username":"testuser","password":"123"}            | 密码长度至少 8 位
            {"username":"testuser","password":"Test1234","phone":"abc"} | 手机号格式不正确
            """)
    void register_validationFail(String body, String expectedMsg) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(expectedMsg));
    }

    @Test
    void register_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_ok() throws Exception {
        LoginResponse resp = LoginResponse.of("jwt.xxx", "rft_xxx");

        when(userService.login(any(), eq("device-1"), isNull())).thenReturn(resp);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Device-Id", "device-1")
                        .content("""
                                {"username":"zhangsan","password":"Test1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.accessToken").value("jwt.xxx"))
                .andExpect(jsonPath("$.data.refreshToken").value("rft_xxx"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void login_missingDeviceId_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"zhangsan","password":"Test1234"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_emptyUsername_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Device-Id", "device-1")
                        .content("""
                                {"username":"","password":"Test1234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void logout_ok() throws Exception {
        doNothing().when(userService).logout(anyString());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"rft_xxx"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE));

        verify(userService).logout("rft_xxx");
    }

    @Test
    void logout_emptyRefreshToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void refresh_ok() throws Exception {
        LoginResponse resp = LoginResponse.of("new-jwt.xxx", "rft_new");
        when(userService.refresh("rft_old")).thenReturn(resp);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"rft_old"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.accessToken").value("new-jwt.xxx"))
                .andExpect(jsonPath("$.data.refreshToken").value("rft_new"));

        verify(userService).refresh("rft_old");
    }

    @Test
    void refresh_emptyRefreshToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
