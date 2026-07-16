package com.jolumn.livemallwebsocket;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * livemall-websocket — WebSocket 长连接服务.
 *
 * <p>承担功能点：
 * <ul>
 *   <li>3.2.1 开播 / 3.2.2 关播</li>
 *   <li>3.2.3 进直播间 / 3.2.4 离开直播间</li>
 *   <li>3.2.5 在线人数统计 / 3.2.6 心跳检测</li>
 *   <li>3.2.7 单端观看踢人 / 3.2.8 断线重连</li>
 *   <li>3.3.1 发送弹幕 / 3.3.2 送礼物</li>
 *   <li>3.4.5 秒杀结果推送（消费 Kafka → Dubbo WsPushService）</li>
 * </ul>
 *
 * <h3>VT 纪律（1.4 + 2.4）</h3>
 * <ul>
 *   <li>JSR-356 {@code @ServerEndpoint} 默认跑 Tomcat 线程池，
 *       由 {@link com.jolumn.livemallwebsocket.config.WebSocketConfig} 注入 VT Executor</li>
 *   <li>禁用 {@code synchronized(session) + getBasicRemote()}（VT pinning）</li>
 *   <li>必须用 {@code session.getAsyncRemote().sendText()}（无锁 + 不 pin）</li>
 *   <li>超时清理 / 异步入库用 {@code Thread.startVirtualThread}，不用 CompletableFuture.runAsync</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.livemallwebsocket",
        "com.jolumn.livemallcommon"
})
@EnableDubbo
@EnableScheduling
public class LivemallWebsocketApplication {

    static void main(String[] args) {
        SpringApplication.run(LivemallWebsocketApplication.class, args);
    }
}
