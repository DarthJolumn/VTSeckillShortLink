package com.jolumn.vtslshortlinkapi.grpc;

import com.jolumn.vtslcommon.grpc.key.Empty;
import com.jolumn.vtslcommon.grpc.key.KeyResponse;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class KeyServiceClient {

    private static final Logger log = LoggerFactory.getLogger(KeyServiceClient.class);

    private final GrpcClientConfig grpcClientConfig;

    public KeyServiceClient(GrpcClientConfig grpcClientConfig) {
        this.grpcClientConfig = grpcClientConfig;
    }

    public String getKey() {
        try {
            KeyResponse response = grpcClientConfig.getStub()
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getKey(Empty.getDefaultInstance());
            return response.getKey();
        } catch (StatusRuntimeException e) {
            log.error("KGS GetKey failed: {}", e.getStatus());
            throw new RuntimeException("Failed to generate short key", e);
        }
    }
}
