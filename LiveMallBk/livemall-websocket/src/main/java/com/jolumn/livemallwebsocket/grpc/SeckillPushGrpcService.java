package com.jolumn.livemallwebsocket.grpc;

import com.jolumn.livemallcommon.grpc.SeckillPushGrpc;
import com.jolumn.livemallcommon.grpc.SeckillPushOuterClass;
import com.jolumn.livemallwebsocket.manager.WsSessionManager;
import com.jolumn.livemallwebsocket.model.WsSession;
import tools.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Map;

@GrpcService
public class SeckillPushGrpcService extends SeckillPushGrpc.SeckillPushImplBase {

    private static final Logger log = LoggerFactory.getLogger(SeckillPushGrpcService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final WsSessionManager sessionManager;

    public SeckillPushGrpcService(WsSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void pushResult(SeckillPushOuterClass.SeckillPushRequest request,
                           StreamObserver<SeckillPushOuterClass.SeckillPushResponse> responseObserver) {
        Collection<WsSession> sessions = sessionManager.findByUserId(request.getUserId());
        SeckillPushOuterClass.SeckillPushResponse.Builder resp = SeckillPushOuterClass.SeckillPushResponse.newBuilder();

        if (!sessions.isEmpty()) {
            boolean delivered = false;
            for (WsSession ws : sessions) {
                if (!ws.getSession().isOpen()) continue;
                try {
                    String json = mapper.writeValueAsString(Map.of(
                            "type", "SEC_KILL_RESULT",
                            "data", Map.of(
                                    "orderNo", request.getOrderNo(),
                                    "ok", request.getSuccess(),
                                    "reason", request.getSuccess() ? "success" : "failed",
                                    "message", request.getMessage(),
                                    "timestamp", request.getTimestamp())));
                    ws.getSession().getAsyncRemote().sendText(json);
                    delivered = true;
                } catch (Exception e) {
                    log.warn("gRPC 推送 (session={}): {}", ws.getSessionId(), e.getMessage());
                }
            }
            resp.setDelivered(delivered);
            if (delivered) {
                log.info("gRPC 秒杀结果推送: userId={}, orderNo={}", request.getUserId(), request.getOrderNo());
            }
        } else {
            resp.setDelivered(false).setReason("user_offline");
            log.debug("用户不在线: userId={}", request.getUserId());
        }

        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }
}
