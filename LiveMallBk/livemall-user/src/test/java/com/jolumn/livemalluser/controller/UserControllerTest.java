package com.jolumn.livemalluser.controller;

import com.jolumn.livemallcommon.dto.Result;
import com.jolumn.livemallcommon.exception.GlobalExceptionHandler;
import com.jolumn.livemalluser.dto.DeviceInfo;
import com.jolumn.livemalluser.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

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
    void getDevices_empty() throws Exception {
        when(userService.getDevices(anyLong()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/user/devices")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
