package com.jolumn.vtslshortlinkapi.controller;

import com.jolumn.vtslcommon.annotation.PublicApi;
import com.jolumn.vtslcommon.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    @PublicApi
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "UP"));
    }
}
