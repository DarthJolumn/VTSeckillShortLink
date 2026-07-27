package com.jolumn.vtsluser.controller;

import com.jolumn.vtslcommon.dto.Result;
import com.jolumn.vtslcommon.exception.BizException;
import com.jolumn.vtslcommon.exception.GlobalExceptionHandler;
import com.jolumn.vtsluser.dto.DeviceInfo;
import com.jolumn.vtsluser.entity.User;
import com.jolumn.vtsluser.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController(userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getDevices_ok() throws Exception {
        when(userService.getDevices(anyLong()))
                .thenReturn(List.of(DeviceInfo.of("d1", false), DeviceInfo.of("d2", false)));

        mockMvc.perform(get("/user/devices")
                        .header("X-User-Id", "1")
                        .header("X-Device-Id", "d1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data[0].deviceId").value("d1"))
                .andExpect(jsonPath("$.data[0].current").value(true));
    }

    @Test
    void getProfile_ok() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setNickname("测试用户");
        user.setAvatar("https://example.com/avatar.png");
        user.setPhone("13800138000");
        user.setRole(1);
        user.setStatus(1);

        when(userService.findById(1L)).thenReturn(user);

        mockMvc.perform(get("/user/profile")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.nickname").value("测试用户"))
                .andExpect(jsonPath("$.data.avatar").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.phone").value("13800138000"))
                .andExpect(jsonPath("$.data.role").value(1))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void getProfile_userNotFound_returns404() throws Exception {
        when(userService.findById(999L))
                .thenThrow(new BizException(404, "用户不存在"));

        mockMvc.perform(get("/user/profile")
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void getDevices_empty() throws Exception {
        when(userService.getDevices(anyLong()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/user/devices")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void kickDevice_ok() throws Exception {
        doNothing().when(userService).kickDevice(1L, "d1");

        mockMvc.perform(delete("/user/devices/d1")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE));

        verify(userService).kickDevice(1L, "d1");
    }

    @Test
    void kickDevice_notFound() throws Exception {
        doThrow(new BizException(404, "设备不在线"))
                .when(userService).kickDevice(1L, "d-none");

        mockMvc.perform(delete("/user/devices/d-none")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("设备不在线"));
    }
}
