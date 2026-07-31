package com.jolumn.vtslproduct;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.jolumn.vtslproduct",
        "com.jolumn.vtslcommon.filter",
        "com.jolumn.vtslcommon.interceptor",
        "com.jolumn.vtslcommon.util",
        "com.jolumn.vtslcommon.exception",
        "com.jolumn.vtslcommon.dto",
        "com.jolumn.vtslcommon.annotation"
})
@EnableDubbo
public class VtslProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(VtslProductApplication.class, args);
    }
}
