package com.jolumn.vtsluser.dto;

import java.io.Serializable;

public class LoginResponse implements Serializable {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String tokenType;

    public static LoginResponse of(String accessToken, String refreshToken) {
        LoginResponse r = new LoginResponse();
        r.accessToken = accessToken;
        r.refreshToken = refreshToken;
        r.expiresIn = 900;
        r.tokenType = "Bearer";
        return r;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
