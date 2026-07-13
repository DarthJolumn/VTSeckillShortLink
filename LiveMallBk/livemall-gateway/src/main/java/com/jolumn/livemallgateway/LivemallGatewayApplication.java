package com.jolumn.livemallgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * livemall-gateway — WebFlux 网关服务.
 *
 * <p>唯一响应式服务。不做业务逻辑，只负责：
 * <ol>
 *   <li>路由转发（lb:// 通过 Nacos）</li>
 *   <li>JWT 鉴权（HMAC-SHA256 纯 CPU 验签）</li>
 *   <li>签名验签（ReactiveRedis Nonce）</li>
 *   <li>Sentinel 限流</li>
 *   <li>CORS 跨域</li>
 * </ol>
 */
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.livemallcommon",
        "com.jolumn.livemallgateway"
})
public class LivemallGatewayApplication {

    static void main(String[] args) {
        SpringApplication.run(LivemallGatewayApplication.class, args);
    }
}
