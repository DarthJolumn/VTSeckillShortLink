package com.jolumn.livemallshortlink;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.jolumn.livemallshortlink",
        "com.jolumn.livemallcommon.interceptor",
        "com.jolumn.livemallcommon.util",
        "com.jolumn.livemallcommon.exception",
        "com.jolumn.livemallcommon.dto",
        "com.jolumn.livemallcommon.annotation"
})
@EnableDubbo
@EnableScheduling
public class LivemallShortlinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(LivemallShortlinkApplication.class, args);
    }
}
