package com.jolumn.vtslseckill.model.enums;

public enum BizErrorCode {
    // ========== 系统级 (500-599) ==========
    SYSTEM_BUSY(503, "系统繁忙，请稍后重试"),
    SYSTEM_ERROR(500, "系统异常，请联系管理员"),

    // ========== 活动校验 (4001-4009) ==========
    ACTIVITY_NOT_EXIST(4001, "活动不存在"),
    ACTIVITY_NOT_STARTED(4002, "活动未开始"),
    ACTIVITY_ENDED(4003, "活动已结束"),
    ACTIVITY_NOT_IN_TIME(4004, "不在活动时间范围内"),
    ACTIVITY_NOT_INIT(4005, "活动未初始化"),

    // ========== 库存相关 (4010-4019) ==========
    STOCK_NOT_ENOUGH(4010, "库存不足，已被抢光"),

    // ========== 用户相关 (4020-4029) ==========
    USER_REPEAT_ORDER(4020, "您已参与过该活动，请勿重复下单"),

    // ========== 成功 (200) ==========
    SUCCESS(200, "ok");

    private final int code;
    private final String msg;

    BizErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() { return code; }
    public String getMsg() { return msg; }
}