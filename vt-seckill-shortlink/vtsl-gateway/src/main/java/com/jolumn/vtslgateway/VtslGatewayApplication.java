package com.jolumn.vtslgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * vtsl-gateway — WebFlux 网关服务.
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
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.jolumn.vtslcommon", "com.jolumn.vtslgateway"},
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.jolumn.vtslcommon.exception.GlobalExceptionHandler.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.jolumn\\.vtslcommon\\.filter\\..*"
                )
        }
)
public class VtslGatewayApplication {

    static void main(String[] args) {
        SpringApplication.run(VtslGatewayApplication.class, args);
    }
}
