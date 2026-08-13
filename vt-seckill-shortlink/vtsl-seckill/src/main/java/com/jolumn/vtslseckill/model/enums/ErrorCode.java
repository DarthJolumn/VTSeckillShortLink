package com.jolumn.vtslseckill.model.enums;

public interface ErrorCode {
    int getCode();    // 数字状态码，如 404, 4001
    String getMsg();  // 提示信息，如 "活动不存在"
}