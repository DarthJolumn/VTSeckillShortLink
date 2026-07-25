package com.jolumn.livemallwebsocket.config;

import com.jolumn.livemallwebsocket.manager.WsSessionManager;
import com.jolumn.livemallwebsocket.model.WsSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;

@Configuration
public class RedisPushSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RedisPushSubscriber.class);
    private static final String WS_PUSH_TOPIC = "ws:push:seckill-result";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final WsSessionManager sessionManager;

    public RedisPushSubscriber(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(
            org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(seckillResultListener(), new PatternTopic(WS_PUSH_TOPIC));
        log.info("Redis Pub/Sub 已订阅: {}", WS_PUSH_TOPIC);
        return container;
    }

    @Bean
    MessageListener seckillResultListener() {
        return (Message message, byte[] pattern) -> {
            try {
                JsonNode node = mapper.readTree(message.getBody());
                Long userId = node.get("userId").asLong();
                String orderNo = node.get("orderNo").asText();
                boolean ok = node.get("ok").asBoolean();
                String msg = node.get("message").asText();
                long timestamp = node.get("timestamp").asLong();

                Collection<WsSession> sessions = sessionManager.findByUserId(userId);
                if (sessions.isEmpty()) {
                    log.debug("用户不在线，跳过推送: userId={}", userId);
                    return;
                }

                String json = mapper.writeValueAsString(Map.of(
                        "type", "SEC_KILL_RESULT",
                        "data", Map.of(
                                "orderNo", orderNo,
                                "ok", ok,
                                "reason", ok ? "success" : "failed",
                                "message", msg,
                                "timestamp", timestamp)));

                for (WsSession ws : sessions) {
                    if (!ws.getSession().isOpen()) continue;
                    try {
                        ws.getSession().getAsyncRemote().sendText(json);
                    } catch (Exception e) {
                        log.warn("秒杀结果推送失败: session={}, {}", ws.getSessionId(), e.getMessage());
                    }
                }
                log.info("秒杀结果已推送: userId={}, orderNo={}, sessions={}", userId, orderNo, sessions.size());
            } catch (Exception e) {
                log.error("处理 Redis Pub/Sub 消息异常", e);
            }
        };
    }
}
