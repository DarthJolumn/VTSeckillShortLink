package com.jolumn.livemallcommon.dto;

import java.io.Serializable;

public class UserDTO implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer role;
    private Integer status;

    public UserDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "UserDTO{id=" + id + ", username='" + username + "', nickname='" + nickname
                + "', role=" + role + ", status=" + status + "}";
    }
}