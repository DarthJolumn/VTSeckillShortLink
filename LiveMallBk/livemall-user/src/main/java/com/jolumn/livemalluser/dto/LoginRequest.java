package com.jolumn.livemalluser.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 50, message = "用户名长度须在 1~50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 255, message = "密码长度须在 1~255 之间")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
