package com.jolumn.vtslshortlink;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.jolumn.vtslshortlink",
        "com.jolumn.vtslcommon.filter",
        "com.jolumn.vtslcommon.interceptor",
        "com.jolumn.vtslcommon.util",
        "com.jolumn.vtslcommon.exception",
        "com.jolumn.vtslcommon.dto",
        "com.jolumn.vtslcommon.annotation"
})
@EnableDubbo
@EnableScheduling
public class VtslShortlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(VtslShortlinkApplication.class, args);
    }
}
