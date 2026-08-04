package com.jolumn.vtslkgs.grpc;

import com.jolumn.vtslkgs.service.KeyService;
import com.jolumn.vtslcommon.grpc.key.Empty;
import com.jolumn.vtslcommon.grpc.key.KeyResponse;
import com.jolumn.vtslcommon.grpc.key.KeyServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KeyGrpcService extends KeyServiceGrpc.KeyServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(KeyGrpcService.class);

    private final KeyService keyService;

    public KeyGrpcService(KeyService keyService) {
        this.keyService = keyService;
    }

    @Override
    public void getKey(Empty request, StreamObserver<KeyResponse> responseObserver) {
        try {
            String key = keyService.getKey();
            KeyResponse response = KeyResponse.newBuilder().setKey(key).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("GetKey failed", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("key generation failed: " + e.getMessage()).asRuntimeException()
            );
        }
    }
}
