package com.jolumn.vtslseckill.model.enums;

/**
 * Kafka 发送结果。语义按发送方式区分：
 *  - {@link #CONFIRMED}：同步发送，broker 已 ack（消息确认送达）
 *  - {@link #ACCEPTED} ：异步发送，pending 已落（受理，不代表送达；送达由回调/补偿保证）
 *  - {@link #TRANSIENT}：瞬时失败（超时/网络抖动）→ 统一回补
 *  - {@link #FATAL}    ：不可恢复（producer 关闭/序列化失败）→ 统一回补
 *
 * <p>设计：上层（placeOrder）对非 CONFIRMED/ACCEPTED 一律幂等回补库存 + 503，
 * 不区分 TRANSIENT/FATAL 的回补行为（区分仅用于日志/告警级别）。</p>
 */
public enum SendOutcome {
    /** 同步确认送达（broker ack） */
    CONFIRMED,
    /** 异步受理（pending 已落，不代表送达） */
    ACCEPTED,
    /** 瞬时失败（可重试，但本设计不做应用层重试，统一回补） */
    TRANSIENT,
    /** 不可恢复失败（producer 关闭等） */
    FATAL
}
