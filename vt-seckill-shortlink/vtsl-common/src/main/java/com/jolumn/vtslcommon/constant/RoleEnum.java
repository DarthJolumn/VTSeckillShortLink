package com.jolumn.vtslcommon.constant;

public enum RoleEnum {
    AUDIENCE(1, "观众"),
    ANCHOR(2, "主播"),
    ADMIN(3, "管理员");

    private final int code;
    private final String label;

    RoleEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static RoleEnum fromCode(int code) {
        for (RoleEnum r : values()) {
            if (r.code == code) return r;
        }
        throw new IllegalArgumentException("无效角色码: " + code);
    }

    public boolean isAnchorOrAbove() {
        return this == ANCHOR || this == ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}