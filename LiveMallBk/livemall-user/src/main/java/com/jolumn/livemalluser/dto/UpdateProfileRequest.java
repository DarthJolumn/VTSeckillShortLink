package com.jolumn.livemalluser.dto;

/**
 * 更新个人资料请求
 */
public class UpdateProfileRequest {

    private String nickname;
    private String avatar;
    private String phone;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
