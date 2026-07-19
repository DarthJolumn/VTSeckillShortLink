package com.jolumn.livemallwebsocket.grpc;

import com.jolumn.livemallcommon.grpc.SeckillPushGrpc;
import com.jolumn.livemallcommon.grpc.SeckillPushOuterClass;
import com.jolumn.livemallwebsocket.manager.WsSessionManager;
import com.jolumn.livemallwebsocket.model.WsSession;
import tools.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
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
        WsSession ws = sessionManager.findByUserId(request.getUserId());
        SeckillPushOuterClass.SeckillPushResponse.Builder resp = SeckillPushOuterClass.SeckillPushResponse.newBuilder();

        if (ws != null && ws.getSession().isOpen()) {
            try {
                String json = mapper.writeValueAsString(Map.of(
                        "type", "SEC_KILL_RESULT",
                        "data", Map.of(
                                "orderNo", request.getOrderNo(),
                                "success", request.getSuccess(),
                                "message", request.getMessage(),
                                "timestamp", request.getTimestamp())));
                ws.getSession().getAsyncRemote().sendText(json);
                resp.setDelivered(true);
                log.info("gRPC 秒杀结果推送成功: userId={}, orderNo={}", request.getUserId(), request.getOrderNo());
            } catch (Exception e) {
                resp.setDelivered(false).setReason("send_failed: " + e.getMessage());
                log.error("gRPC 推送失败: userId={}", request.getUserId(), e);
            }
        } else {
            resp.setDelivered(false).setReason("user_offline");
            log.debug("用户不在线: userId={}", request.getUserId());
        }

        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }
}
