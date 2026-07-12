package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.exception.GlobalExceptionHandler;
import com.jolumn.livemallcommon.util.IdempotencyService;
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
}
