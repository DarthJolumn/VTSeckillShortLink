package com.jolumn.vtslkgs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * vtsl-shortlink-keygenerator — KGS 短码预生成服务（端口 8082，gRPC 50051）。
 *
 * <p>纯 gRPC + Health 服务，无 Servlet 鉴权需求；扫描 vtsl-common 的
 * exception/dto 使全局异常处理与统一响应体生效。
 */
@SpringBootApplication(scanBasePackages = {
        "com.jolumn.vtslkgs",
        "com.jolumn.vtslcommon.exception",
        "com.jolumn.vtslcommon.dto"
})
public class VtslKeyGeneratorApplication {
    public static void main(String[] args) {
        SpringApplication.run(VtslKeyGeneratorApplication.class, args);
    }
}
